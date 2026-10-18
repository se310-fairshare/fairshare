package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.CounterpartyBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.CreateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.dto.IndividualDebtResponse;
import nz.ac.auckland.se310.fairshare.dto.NetBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.UpdateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import nz.ac.auckland.se310.fairshare.exception.IndividualDebtAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidDebtEntryException;
import nz.ac.auckland.se310.fairshare.exception.UnsupportedCurrencyException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.IndividualDebt;
import nz.ac.auckland.se310.fairshare.model.Settlement;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.IndividualDebtRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #2: individual debt creation, authorization and the combined net-balance calculation.
 * Runs without a database - all repositories are stubbed. Group balances go through a real
 * SettlementService, so these tests check agreement with the group's own settle-up plan.
 */
class IndividualDebtServiceTest {

    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final long CAROL = 30L;
    private static final long GROUP_ID = 1L;
    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 8, 1);

    private final IndividualDebtRepository individualDebtRepository = mock(IndividualDebtRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExpenseGroupRepository groupRepository = mock(ExpenseGroupRepository.class);
    private final ExpenseService expenseService = mock(ExpenseService.class);
    private final SettlementRepository settlementRepository = mock(SettlementRepository.class);

    /** Stub rates as "FROM->TO" -> rate; a missing pair means the rate is unavailable. */
    private final Map<String, BigDecimal> rates = new HashMap<>();
    private final ExchangeRateProvider exchangeRateProvider = (from, to, date) -> {
        BigDecimal rate = rates.get(from + "->" + to);
        if (rate == null) {
            throw new ExchangeRateUnavailableException(from, to);
        }
        return rate;
    };

    /** Groups each pair shares, as "userId:userId", built up by sharedBy. */
    private final Map<String, List<ExpenseGroup>> sharedGroups = new HashMap<>();

    private User alice;
    private User bob;
    private User carol;
    private IndividualDebtService service;

    @BeforeEach
    void setUp() {
        alice = user(ALICE, "alice", User.Currency.NZD);
        bob = user(BOB, "bob", User.Currency.NZD);
        carol = user(CAROL, "carol", User.Currency.NZD);

        when(individualDebtRepository.save(any(IndividualDebt.class))).then(returnsFirstArg());
        when(individualDebtRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(List.of());
        when(groupRepository.findSharedGroups(anyLong(), anyLong())).thenReturn(List.of());

        SettlementService settlementService = new SettlementService(groupRepository, userRepository,
                expenseService, settlementRepository);

        // No Spring proxy exists in this test, so "self" is wired to a plain instance; that's
        // equivalent here since there's no transactional interception to route through either way.
        service = new IndividualDebtService(individualDebtRepository, userRepository, groupRepository,
                settlementService, new CurrencyService(), exchangeRateProvider, null);
        service = new IndividualDebtService(individualDebtRepository, userRepository, groupRepository,
                settlementService, new CurrencyService(), exchangeRateProvider, service);
    }

    @Test
    void ac9_selfDebtIsRejectedOnCreate() {
        whenLookedUpByUsername(alice);

        assertThatThrownBy(() -> service.createDebt(ALICE, createRequest("alice", null)))
                .isInstanceOf(InvalidDebtEntryException.class);
        verify(individualDebtRepository, never()).save(any());
    }

    @Test
    void ac1_createPersistsCurrentUserAsPayerAndSelectedUserAsDebtor() {
        whenLookedUpByUsername(bob);

        IndividualDebtResponse response = service.createDebt(ALICE, createRequest("bob", null));

        assertThat(response.payerUserId()).isEqualTo(ALICE);
        assertThat(response.debtorUserId()).isEqualTo(BOB);
        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    void createWithoutCurrencyUsesTheCreatorsHomeCurrency() {
        whenLookedUpByUsername(bob);

        assertThat(service.createDebt(ALICE, createRequest("bob", null)).currency()).isEqualTo("NZD");
    }

    @Test
    void aUserWithoutAHomeCurrencyFallsBackToNzd() {
        when(alice.getCurrency()).thenReturn(null);
        whenLookedUpByUsername(bob);

        assertThat(service.createDebt(ALICE, createRequest("bob", null)).currency()).isEqualTo("NZD");
        assertThat(service.getNetBalance(ALICE, BOB).currency()).isEqualTo("NZD");
    }

    @Test
    void createStoresTheChosenCurrencyInCanonicalForm() {
        whenLookedUpByUsername(bob);

        assertThat(service.createDebt(ALICE, createRequest("bob", " usd ")).currency()).isEqualTo("USD");
    }

    @Test
    void createRejectsAnUnsupportedCurrency() {
        whenLookedUpByUsername(bob);

        assertThatThrownBy(() -> service.createDebt(ALICE, createRequest("bob", "XYZ")))
                .isInstanceOf(UnsupportedCurrencyException.class);
        verify(individualDebtRepository, never()).save(any());
    }

    @Test
    void ac7_onlyCreatorCanEdit() {
        storedDebt(new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "NZD", "Coffee", ENTRY_DATE));

        assertThatThrownBy(() -> service.updateDebt(1L, BOB, updateRequest(null)))
                .isInstanceOf(IndividualDebtAccessDeniedException.class);
        verify(individualDebtRepository, never()).save(any());
    }

    @Test
    void ac7_theDebtorCannotDeleteAnEntryEvenIfTheyCreatedIt() {
        // Bob recorded that he owes Alice; only Alice can write that off.
        storedDebt(new IndividualDebt(bob, alice, bob, new BigDecimal("10.00"), "NZD", "Coffee", ENTRY_DATE));

        assertThatThrownBy(() -> service.deleteDebt(1L, BOB))
                .isInstanceOf(IndividualDebtAccessDeniedException.class)
                .hasMessage("Only the person who is owed can delete this entry");
        verify(individualDebtRepository, never()).delete(any());
    }

    @Test
    void ac7_thePersonOwedCanDeleteAnEntrySomeoneElseCreated() {
        IndividualDebt debt = new IndividualDebt(bob, alice, bob, new BigDecimal("10.00"), "NZD", "Coffee", ENTRY_DATE);
        storedDebt(debt);

        service.deleteDebt(1L, ALICE);

        verify(individualDebtRepository).delete(debt);
    }

    @Test
    void ac8_creatorEditRecalculatesTheEntry() {
        storedDebt(new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "NZD", "Coffee", ENTRY_DATE));

        IndividualDebtResponse response = service.updateDebt(1L, ALICE, updateRequest("AUD"));

        assertThat(response.amount()).isEqualByComparingTo("15.00");
        assertThat(response.currency()).isEqualTo("AUD");
        assertThat(response.description()).isEqualTo("Dinner");
    }

    @Test
    void ac8_editWithoutACounterpartyOrCurrencyKeepsThem() {
        storedDebt(new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "NZD", "Coffee", ENTRY_DATE));

        IndividualDebtResponse response = service.updateDebt(1L, ALICE, updateRequest(null));

        assertThat(response.payerUserId()).isEqualTo(ALICE);
        assertThat(response.debtorUserId()).isEqualTo(BOB);
        assertThat(response.currency()).isEqualTo("NZD");
    }

    @Test
    void ac8_creatorCanCorrectWhoOwesThemAndStaysThePersonOwed() {
        storedDebt(new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "NZD", "Coffee", ENTRY_DATE));
        whenLookedUpByUsername(carol);

        IndividualDebtResponse response = service.updateDebt(1L, ALICE, updateRequest("carol", null));

        assertThat(response.payerUserId()).isEqualTo(ALICE);
        assertThat(response.debtorUserId()).isEqualTo(CAROL);
    }

    @Test
    void ac9_selfDebtIsRejectedOnUpdate() {
        storedDebt(new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "NZD", "Coffee", ENTRY_DATE));
        whenLookedUpByUsername(alice);

        assertThatThrownBy(() -> service.updateDebt(1L, ALICE, updateRequest("alice", null)))
                .isInstanceOf(InvalidDebtEntryException.class)
                .hasMessage("The counterparty cannot be yourself");
        verify(individualDebtRepository, never()).save(any());
    }

    @Test
    void ac6_noEntriesOrGroupDebtsMeansSettled() {
        NetBalanceResponse balance = service.getNetBalance(ALICE, BOB);

        assertThat(balance.settled()).isTrue();
        assertThat(balance.amount()).isEqualByComparingTo("0.00");
        assertThat(balance.currency()).isEqualTo("NZD");
        assertThat(balance.fromUserId()).isNull();
        assertThat(balance.toUserId()).isNull();
    }

    @Test
    void ac3_combinesIndividualEntryWithGroupSplitDebt() {
        // Group expense: Bob paid 80, split evenly, so Alice owes Bob 40.
        ExpenseGroup group = sharedGroup(User.Currency.NZD, alice, bob);
        groupExpenses(expense(1L, bob, "80.00", ALICE, BOB));
        // Individual entry: Bob owes Alice 15.
        entriesBetweenAliceAndBob(new IndividualDebt(bob, alice, bob, new BigDecimal("15.00"), "NZD", "Taxi", ENTRY_DATE));
        sharedBy(group, alice, bob);

        NetBalanceResponse balance = service.getNetBalance(ALICE, BOB);

        // 40 (Alice owes Bob from the group split) - 15 (Bob owes Alice individually) = 25
        assertThat(balance.settled()).isFalse();
        assertThat(balance.fromUserId()).isEqualTo(ALICE);
        assertThat(balance.toUserId()).isEqualTo(BOB);
        assertThat(balance.amount()).isEqualByComparingTo("25.00");
        assertThat(balance.entries()).hasSize(1);
    }

    @Test
    void ac3_threeWayGroupBalanceMatchesTheGroupsSimplifiedSettleUpPlan() {
        // Bob paid 20 for Alice and Bob; Carol paid 20 for Bob and Carol. Alice owes Bob 10 and
        // Bob owes Carol 10, which the group simplifies to Alice paying Carol 10 directly.
        ExpenseGroup group = sharedGroup(User.Currency.NZD, alice, bob, carol);
        sharedBy(group, alice, bob);
        sharedBy(group, alice, carol);
        groupExpenses(expense(1L, bob, "20.00", ALICE, BOB), expense(2L, carol, "20.00", BOB, CAROL));

        assertThat(service.getNetBalance(ALICE, BOB).settled()).isTrue();
        NetBalanceResponse aliceAndCarol = service.getNetBalance(ALICE, CAROL);
        assertThat(aliceAndCarol.fromUserId()).isEqualTo(ALICE);
        assertThat(aliceAndCarol.toUserId()).isEqualTo(CAROL);
        assertThat(aliceAndCarol.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void ac3_threeWayGroupIsSettledOnceThePlannedPaymentIsMarkedPaid() {
        ExpenseGroup group = sharedGroup(User.Currency.NZD, alice, bob, carol);
        sharedBy(group, alice, bob);
        sharedBy(group, alice, carol);
        sharedBy(group, bob, carol);
        groupExpenses(expense(1L, bob, "20.00", ALICE, BOB), expense(2L, carol, "20.00", BOB, CAROL));
        Settlement paid = new Settlement(group, alice, carol, new BigDecimal("10.00"));
        paid.setSettlementDate(LocalDate.now());
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(paid));

        // Previously Alice still owed Bob 10 here, and Carol appeared to owe Alice 10.
        assertThat(service.getNetBalance(ALICE, BOB).settled()).isTrue();
        assertThat(service.getNetBalance(ALICE, CAROL).settled()).isTrue();
        assertThat(service.getNetBalance(BOB, CAROL).settled()).isTrue();
    }

    @Test
    void currenciesAreConvertedIntoTheViewersHomeCurrencyBeforeBeingCombined() {
        rates.put("USD->NZD", new BigDecimal("1.70"));
        rates.put("AUD->NZD", new BigDecimal("1.10"));
        // AUD group: Bob paid 20 split evenly, so Alice owes Bob AUD 10 = NZD 11.
        ExpenseGroup group = sharedGroup(User.Currency.AUD, alice, bob);
        sharedBy(group, alice, bob);
        groupExpenses(expense(1L, bob, "20.00", ALICE, BOB));
        // Individual entry: Bob owes Alice USD 10 = NZD 17.
        entriesBetweenAliceAndBob(new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "USD", "Tickets", ENTRY_DATE));

        NetBalanceResponse balance = service.getNetBalance(ALICE, BOB);

        assertThat(balance.currency()).isEqualTo("NZD");
        assertThat(balance.fromUserId()).isEqualTo(BOB);
        assertThat(balance.toUserId()).isEqualTo(ALICE);
        assertThat(balance.amount()).isEqualByComparingTo("6.00");
        // The breakdown keeps what was actually recorded.
        assertThat(balance.entries().getFirst().amount()).isEqualByComparingTo("10.00");
        assertThat(balance.entries().getFirst().currency()).isEqualTo("USD");
    }

    @Test
    void anUnavailableRateExplainsThatTheBalanceCannotBeShown() {
        entriesBetweenAliceAndBob(new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "USD", "Tickets", ENTRY_DATE));

        assertThatThrownBy(() -> service.getNetBalance(ALICE, BOB))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("so this balance cannot be shown");
    }

    @Test
    void ac2_entriesInBothDirectionsNetToOneFigureWithTheEntriesKeptAsTheBreakdown() {
        entriesBetweenAliceAndBob(
                new IndividualDebt(alice, alice, bob, new BigDecimal("50.00"), "NZD", "Concert", ENTRY_DATE),
                new IndividualDebt(bob, bob, alice, new BigDecimal("20.00"), "NZD", "Lunch", ENTRY_DATE));

        for (NetBalanceResponse balance : List.of(service.getNetBalance(ALICE, BOB), service.getNetBalance(BOB, ALICE))) {
            assertThat(balance.fromUserId()).isEqualTo(BOB);
            assertThat(balance.toUserId()).isEqualTo(ALICE);
            assertThat(balance.amount()).isEqualByComparingTo("30.00");
            assertThat(balance.entries()).hasSize(2);
        }
    }

    @Test
    void ac10_netBalanceIsSymmetricRegardlessOfViewpoint() {
        ExpenseGroup group = sharedGroup(User.Currency.NZD, alice, bob);
        sharedBy(group, alice, bob);
        groupExpenses(expense(1L, bob, "80.00", ALICE, BOB));
        entriesBetweenAliceAndBob(new IndividualDebt(bob, alice, bob, new BigDecimal("15.00"), "NZD", "Taxi", ENTRY_DATE));

        NetBalanceResponse fromAlice = service.getNetBalance(ALICE, BOB);
        NetBalanceResponse fromBob = service.getNetBalance(BOB, ALICE);

        assertThat(fromAlice.fromUserId()).isEqualTo(fromBob.fromUserId());
        assertThat(fromAlice.toUserId()).isEqualTo(fromBob.toUserId());
        assertThat(fromAlice.amount()).isEqualByComparingTo(fromBob.amount());
        assertThat(fromAlice.settled()).isEqualTo(fromBob.settled());
    }

    @Test
    void ac4_overviewCombinesIndividualDebtCounterpartiesAndGroupmates() {
        // Bob: a counterparty via an individual debt entry, no shared group.
        when(individualDebtRepository.findCounterpartyIds(ALICE)).thenReturn(List.of(BOB));
        IndividualDebt entry = new IndividualDebt(alice, alice, bob, new BigDecimal("12.00"), "NZD", "Snacks", ENTRY_DATE);
        when(individualDebtRepository.findBetweenUsers(ALICE, BOB)).thenReturn(List.of(entry));

        // Carol: a counterparty only because she shares a group with Alice, no individual entries.
        ExpenseGroup group = mock(ExpenseGroup.class);
        UserInGroup aliceMembership = mock(UserInGroup.class);
        when(aliceMembership.getUser()).thenReturn(alice);
        UserInGroup carolMembership = mock(UserInGroup.class);
        when(carolMembership.getUser()).thenReturn(carol);
        when(group.getMembers()).thenReturn(Set.of(aliceMembership, carolMembership));
        when(groupRepository.findByMembersUserIdOrderByCreatedAtDesc(ALICE)).thenReturn(List.of(group));

        List<CounterpartyBalanceResponse> overview = service.getBalancesOverview(ALICE);

        assertThat(overview).extracting(CounterpartyBalanceResponse::counterpartyUserId)
                .containsExactlyInAnyOrder(BOB, CAROL);

        CounterpartyBalanceResponse bobBalance = overview.stream()
                .filter(balance -> balance.counterpartyUserId().equals(BOB)).findFirst().orElseThrow();
        assertThat(bobBalance.settled()).isFalse();
        assertThat(bobBalance.fromUserId()).isEqualTo(BOB);
        assertThat(bobBalance.toUserId()).isEqualTo(ALICE);
        assertThat(bobBalance.amount()).isEqualByComparingTo("12.00");
        assertThat(bobBalance.currency()).isEqualTo("NZD");

        CounterpartyBalanceResponse carolBalance = overview.stream()
                .filter(balance -> balance.counterpartyUserId().equals(CAROL)).findFirst().orElseThrow();
        assertThat(carolBalance.settled()).isTrue();
    }

    private static CreateIndividualDebtRequest createRequest(String counterparty, String currency) {
        return new CreateIndividualDebtRequest(counterparty, new BigDecimal("25.00"), "Lunch", ENTRY_DATE, currency);
    }

    private static UpdateIndividualDebtRequest updateRequest(String currency) {
        return updateRequest(null, currency);
    }

    private static UpdateIndividualDebtRequest updateRequest(String counterparty, String currency) {
        return new UpdateIndividualDebtRequest(counterparty, new BigDecimal("15.00"), "Dinner", ENTRY_DATE, currency);
    }

    private void storedDebt(IndividualDebt debt) {
        when(individualDebtRepository.findById(1L)).thenReturn(Optional.of(debt));
    }

    private void whenLookedUpByUsername(User user) {
        when(userRepository.findByEmailIgnoreCase(user.getUsername())).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase(user.getUsername())).thenReturn(List.of(user));
    }

    private void entriesBetweenAliceAndBob(IndividualDebt... entries) {
        when(individualDebtRepository.findBetweenUsers(ALICE, BOB)).thenReturn(List.of(entries));
        when(individualDebtRepository.findBetweenUsers(BOB, ALICE)).thenReturn(List.of(entries));
    }

    private ExpenseGroup sharedGroup(User.Currency baseCurrency, User creator, User... others) {
        ExpenseGroup group = new ExpenseGroup("Flat", null, baseCurrency, creator);
        for (User other : others) {
            group.addMember(other);
        }
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        when(groupRepository.findByIdAndMembersUserId(any(), any())).thenReturn(Optional.of(group));
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());
        return group;
    }

    private void sharedBy(ExpenseGroup group, User a, User b) {
        List<ExpenseGroup> groups = sharedGroups.computeIfAbsent(a.getId() + ":" + b.getId(), key -> new ArrayList<>());
        groups.add(group);
        when(groupRepository.findSharedGroups(a.getId(), b.getId())).thenReturn(groups);
        when(groupRepository.findSharedGroups(b.getId(), a.getId())).thenReturn(groups);
    }

    private void groupExpenses(ExpenseResponse... expenses) {
        when(expenseService.getExpensesForGroup(any(), any())).thenReturn(List.of(expenses));
    }

    private static ExpenseResponse expense(long id, User payer, String amount, Long... participantIds) {
        return new ExpenseResponse(id, GROUP_ID, payer.getId(), payer.getUsername(), new BigDecimal(amount),
                "Expense " + id, ENTRY_DATE, Instant.now(), List.of(participantIds));
    }

    private User user(long id, String username, User.Currency currency) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.getCurrency()).thenReturn(currency);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }
}
