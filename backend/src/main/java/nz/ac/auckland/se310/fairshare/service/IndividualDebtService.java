package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CounterpartyBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.CreateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.dto.IndividualDebtResponse;
import nz.ac.auckland.se310.fairshare.dto.NetBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.SettlementLine;
import nz.ac.auckland.se310.fairshare.dto.UpdateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import nz.ac.auckland.se310.fairshare.exception.IndividualDebtAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.IndividualDebtNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.InvalidDebtEntryException;
import nz.ac.auckland.se310.fairshare.exception.UserNotFoundException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.IndividualDebt;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.IndividualDebtRepository;
import nz.ac.auckland.se310.fairshare.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IndividualDebtService {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 8; // matches ExpenseService
    // users.currency is nullable, so an account without a home currency still gets one here.
    private static final User.Currency FALLBACK_CURRENCY = User.Currency.NZD;

    private final IndividualDebtRepository individualDebtRepository;
    private final UserRepository userRepository;
    private final ExpenseGroupRepository groupRepository;
    private final SettlementService settlementService;
    private final CurrencyService currencyService;
    private final ExchangeRateProvider exchangeRateProvider;
    // #2: injected lazily so getBalancesOverview calls getNetBalance through the Spring proxy
    // (and therefore under @Transactional) rather than bypassing it via a direct "this" call.
    private final IndividualDebtService self;

    public IndividualDebtService(IndividualDebtRepository individualDebtRepository, UserRepository userRepository,
                                  ExpenseGroupRepository groupRepository, SettlementService settlementService,
                                  CurrencyService currencyService, ExchangeRateProvider exchangeRateProvider,
                                  @Lazy IndividualDebtService self) {
        this.individualDebtRepository = individualDebtRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.settlementService = settlementService;
        this.currencyService = currencyService;
        this.exchangeRateProvider = exchangeRateProvider;
        this.self = self;
    }

    // #2 AC1: the current user is always the payer when an entry is first created.
    @Transactional
    public IndividualDebtResponse createDebt(Long currentUserId, CreateIndividualDebtRequest request) {
        User payer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUserId));
        User debtor = findUser(request.counterpartyIdentifier().trim());

        requireDifferentUsers(payer, debtor); // AC9
        String currency = request.currency() == null
                ? homeCurrency(payer)
                : currencyService.requireSupported(request.currency());

        IndividualDebt debt = new IndividualDebt(payer, payer, debtor,
                request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP), currency,
                request.description().trim(), request.date());
        return toResponse(individualDebtRepository.save(debt), currentUserId);
    }

    @Transactional(readOnly = true)
    public List<IndividualDebtResponse> listMyDebts(Long currentUserId) {
        return individualDebtRepository.findByPayerIdOrDebtorIdOrderByDebtDateDesc(currentUserId, currentUserId)
                .stream()
                .map(debt -> toResponse(debt, currentUserId))
                .toList();
    }

    // #2 AC7, AC8: only the creator can edit. They can correct who owes them, the amount, currency, description
    // or date, but always stay the person owed.
    @Transactional
    public IndividualDebtResponse updateDebt(Long entryId, Long currentUserId, UpdateIndividualDebtRequest request) {
        IndividualDebt debt = individualDebtRepository.findById(entryId)
                .orElseThrow(IndividualDebtNotFoundException::new);
        requireCreator(debt, currentUserId);

        if (request.counterpartyIdentifier() != null) {
            User debtor = findUser(request.counterpartyIdentifier().trim());
            requireDifferentUsers(debt.getPayer(), debtor); // AC9
            debt.setDebtor(debtor);
        }
        String currency = request.currency() == null
                ? debt.getCurrency()
                : currencyService.requireSupported(request.currency());

        debt.setAmount(request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        debt.setCurrency(currency);
        debt.setDescription(request.description().trim());
        debt.setDebtDate(request.date());

        return toResponse(individualDebtRepository.save(debt), currentUserId);
    }

    // #2 AC7: deleting writes the debt off, so only the person who is owed can do it - otherwise the
    // debtor could clear a debt they never paid. Unlike editing, it does not matter who created the entry.
    @Transactional
    public void deleteDebt(Long entryId, Long currentUserId) {
        IndividualDebt debt = individualDebtRepository.findById(entryId)
                .orElseThrow(IndividualDebtNotFoundException::new);
        if (!debt.getPayer().getId().equals(currentUserId)) {
            throw IndividualDebtAccessDeniedException.forDelete();
        }
        individualDebtRepository.delete(debt);
    }

    /**
     * #2 AC2, AC3, AC5, AC6, AC10: the live, combined net balance between two users - every
     * individual debt entry between them, plus what the group settle-up plans of every group they
     * both belong to say one owes the other, reduced to one explicitly-directed figure in the
     * viewer's home currency. Computing this from either user's perspective negates every term,
     * so the direction is the same either way (the amount too, when they share a home currency).
     */
    @Transactional(readOnly = true)
    public NetBalanceResponse getNetBalance(Long currentUserId, Long otherUserId) {
        if (Objects.equals(currentUserId, otherUserId)) {
            throw new InvalidDebtEntryException("Cannot view a balance with yourself");
        }
        User current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUserId));
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + otherUserId));
        CurrencyConverter converter = new CurrencyConverter(homeCurrency(current));

        List<IndividualDebt> entries = individualDebtRepository.findBetweenUsers(currentUserId, otherUserId);

        // Positive means currentUserId owes otherUserId.
        BigDecimal aOwesB = BigDecimal.ZERO;
        for (IndividualDebt entry : entries) {
            BigDecimal amount = converter.convert(entry.getAmount(), entry.getCurrency(), entry.getDebtDate());
            if (entry.getDebtor().getId().equals(currentUserId) && entry.getPayer().getId().equals(otherUserId)) {
                aOwesB = aOwesB.add(amount);
            } else if (entry.getDebtor().getId().equals(otherUserId) && entry.getPayer().getId().equals(currentUserId)) {
                aOwesB = aOwesB.subtract(amount);
            }
        }

        aOwesB = aOwesB.add(computeGroupSplitBalance(currentUserId, otherUserId, converter));
        aOwesB = aOwesB.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<IndividualDebtResponse> breakdown = entries.stream()
                .map(entry -> toResponse(entry, currentUserId))
                .toList();

        return buildNetBalanceResponse(currentUserId, otherUserId, other.getUsername(), aOwesB,
                converter.displayCurrency(), breakdown);
    }

    // #2 AC4: the overview for the individual debts page - every counterparty this user has any relationship with.
    @Transactional(readOnly = true)
    public List<CounterpartyBalanceResponse> getBalancesOverview(Long currentUserId) {
        Set<Long> counterpartyIds = individualDebtRepository.findCounterpartyIds(currentUserId).stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (ExpenseGroup group : groupRepository.findByMembersUserIdOrderByCreatedAtDesc(currentUserId)) {
            for (UserInGroup member : group.getMembers()) {
                Long memberId = member.getUser().getId();
                if (!memberId.equals(currentUserId)) {
                    counterpartyIds.add(memberId);
                }
            }
        }

        return counterpartyIds.stream()
                .map(otherId -> self.getNetBalance(currentUserId, otherId))
                .map(this::toCounterpartyResponse)
                .toList();
    }

    /**
     * #2 AC3: what the group settle-up plans say userA owes userB, across every group they both
     * belong to, in the converter's display currency. Reusing the group's own engine means this
     * always agrees with the group page, including when debts are simplified across the group.
     */
    private BigDecimal computeGroupSplitBalance(Long userAId, Long userBId, CurrencyConverter converter) {
        LocalDate today = LocalDate.now();
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseGroup group : groupRepository.findSharedGroups(userAId, userBId)) {
            String groupCurrency = group.getBaseCurrency().name();
            for (SettlementLine line : settlementService.getLiveSettlementPlan(group.getId(), userAId)) {
                if (line.fromUserId().equals(userAId) && line.toUserId().equals(userBId)) {
                    total = total.add(converter.convert(line.amount(), groupCurrency, today));
                } else if (line.fromUserId().equals(userBId) && line.toUserId().equals(userAId)) {
                    total = total.subtract(converter.convert(line.amount(), groupCurrency, today));
                }
            }
        }
        return total;
    }

    /**
     * #2: converts amounts into one display currency, looking each rate up once per call. Individual
     * entries use the rate on their own date, as expenses do; group balances use today's rate.
     */
    private final class CurrencyConverter {
        private final String displayCurrency;
        private final Map<String, BigDecimal> rates = new HashMap<>();

        CurrencyConverter(String displayCurrency) {
            this.displayCurrency = displayCurrency;
        }

        String displayCurrency() {
            return displayCurrency;
        }

        BigDecimal convert(BigDecimal amount, String currency, LocalDate date) {
            if (currency.equals(displayCurrency)) {
                return amount;
            }
            BigDecimal rate = rates.computeIfAbsent(currency + "@" + date, key -> lookUpRate(currency, date));
            return amount.multiply(rate);
        }

        private BigDecimal lookUpRate(String currency, LocalDate date) {
            try {
                return exchangeRateProvider.getRate(currency, displayCurrency, date)
                        .setScale(RATE_SCALE, RoundingMode.HALF_UP);
            } catch (ExchangeRateUnavailableException e) {
                throw ExchangeRateUnavailableException.forBalance(currency, displayCurrency, e);
            }
        }
    }

    private NetBalanceResponse buildNetBalanceResponse(Long currentUserId, Long otherUserId, String otherUsername,
                                                        BigDecimal aOwesB, String currency,
                                                        List<IndividualDebtResponse> entries) {
        int comparison = aOwesB.compareTo(BigDecimal.ZERO);
        if (comparison == 0) {
            return new NetBalanceResponse(otherUserId, otherUsername, null, null,
                    BigDecimal.ZERO.setScale(MONEY_SCALE), currency, true, entries);
        }
        if (comparison > 0) {
            return new NetBalanceResponse(otherUserId, otherUsername, currentUserId, otherUserId, aOwesB,
                    currency, false, entries);
        }
        return new NetBalanceResponse(otherUserId, otherUsername, otherUserId, currentUserId, aOwesB.negate(),
                currency, false, entries);
    }

    private CounterpartyBalanceResponse toCounterpartyResponse(NetBalanceResponse balance) {
        return new CounterpartyBalanceResponse(balance.otherUserId(), balance.otherUsername(),
                balance.fromUserId(), balance.toUserId(), balance.amount(), balance.currency(), balance.settled());
    }

    private void requireCreator(IndividualDebt debt, Long currentUserId) {
        if (!debt.getCreator().getId().equals(currentUserId)) {
            throw new IndividualDebtAccessDeniedException(); // AC7
        }
    }

    private void requireDifferentUsers(User a, User b) {
        if (a.getId().equals(b.getId())) {
            throw new InvalidDebtEntryException("The counterparty cannot be yourself"); // AC9
        }
    }

    private static String homeCurrency(User user) {
        return (user.getCurrency() == null ? FALLBACK_CURRENCY : user.getCurrency()).name();
    }

    private User findUser(String identifier) {
        return userRepository.findByEmailIgnoreCase(identifier)
                .orElseGet(() -> findUserByUsername(identifier));
    }

    private User findUserByUsername(String username) {
        List<User> matches = userRepository.findAllByUsernameIgnoreCase(username);
        if (matches.isEmpty()) {
            throw new UserNotFoundException("No matching user was found");
        }
        if (matches.size() > 1) {
            throw new InvalidDebtEntryException("Multiple users match that username; use an email address");
        }
        return matches.getFirst();
    }

    private IndividualDebtResponse toResponse(IndividualDebt debt, Long currentUserId) {
        return new IndividualDebtResponse(
                debt.getId(),
                debt.getPayer().getId(),
                debt.getPayer().getUsername(),
                debt.getDebtor().getId(),
                debt.getDebtor().getUsername(),
                debt.getAmount(),
                debt.getCurrency(),
                debt.getDescription(),
                debt.getDebtDate(),
                debt.getCreator().getId().equals(currentUserId));
    }
}
