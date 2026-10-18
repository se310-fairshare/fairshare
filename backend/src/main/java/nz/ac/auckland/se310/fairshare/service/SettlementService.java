package nz.ac.auckland.se310.fairshare.service;


import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.*;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberConflictException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.Settlement;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class SettlementService {

    private final ExpenseGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final SettlementRepository settlementRepository;
    private final GroupBalanceCalculator calculator = new GroupBalanceCalculator();

    public SettlementService(ExpenseGroupRepository groupRepository, UserRepository userRepository,
                             ExpenseService expenseService, SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.settlementRepository = settlementRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getEffectiveBalances(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        return computeEffectiveBalances(groupId, group, expenseService.getExpensesForGroup(groupId, currentUserId));
    }

    private Map<Long, BigDecimal> computeEffectiveBalances(Long groupId, ExpenseGroup group, List<ExpenseResponse> expenses) {
        return calculator.calculate(group, expenses, settlementRepository.findByGroupId(groupId));
    }

    @Transactional(readOnly = true)
    public List<MemberBalance> getBalances(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        Map<Long, BigDecimal> memberBalances = computeEffectiveBalances(groupId, group, expenseService.getExpensesForGroup(groupId, currentUserId));
        return group.getMembers().stream()
                .sorted(Comparator.comparing(member -> member.getUser().getId()))
                .map(member -> new MemberBalance(member.getUser().getId(), memberBalances.getOrDefault(member.getUser().getId(), BigDecimal.ZERO)))
                .toList();
    }

    // #2 AC3: the live settle-up plan, so other features use the same numbers as the group page.
    // Read-only: unlike computeSettlement, nothing is persisted.
    @Transactional(readOnly = true)
    public List<SettlementLine> getLiveSettlementPlan(Long groupId, Long memberId) {
        ExpenseGroup group = requireMemberGroup(groupId, memberId);
        Map<Long, BigDecimal> balances = computeEffectiveBalances(groupId, group,
                expenseService.getExpensesForGroup(groupId, memberId));
        return SettlementPlanner.calculateSettlements(balances);
    }

    @Transactional
    public List<SettlementLine> computeSettlement(Long groupId, Long currentUserId, SettlementRequest request) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);

        Map<Long, BigDecimal> effectiveBalances = computeEffectiveBalances(groupId, group, expenseService.getExpensesForGroup(groupId, currentUserId));
        List<SettlementLine> settlementPlan = SettlementPlanner.calculateSettlements(effectiveBalances);
        persistSettlementPlan(group, groupId, settlementPlan);
        return settlementPlan;
    }

    @Transactional
    public void markSettlementPaid(Long groupId, Long fromUserId, Long toUserId, Long currentUserId) {
        // Only the payer or the recipient may mark the settlement as paid (AC5, AC8)
        if (!List.of(fromUserId, toUserId).contains(currentUserId)) {
            throw new GroupMemberConflictException("User not allowed to marked settlement as paid");
        }

        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);

        Settlement settlement = findOpenSettlement(groupId, fromUserId, toUserId);
        if (settlement == null) {
            settlement = findOpenSettlement(groupId, toUserId, fromUserId);
        }
        if (settlement == null) {
            throw new IllegalArgumentException("No open settlement found for this user pair");
        }

        // Apply the payment to persisted member net balances so stored balances reflect the transfer
        Long payerId = settlement.getFromUser().getId();
        Long recipientId = settlement.getToUser().getId();

        UserInGroup payer = group.getMember(payerId);
        UserInGroup recipient = group.getMember(recipientId);

        BigDecimal amount = settlement.getAmount() == null ? BigDecimal.ZERO : settlement.getAmount();

        if (payer != null) {
            payer.adjustNetBalance(amount.negate());
        }
        if (recipient != null) {
            recipient.adjustNetBalance(amount);
        }

        // Mark the settlement as paid and persist changes
        settlement.setSettlementDate(LocalDate.now());
        settlementRepository.save(settlement);
        groupRepository.save(group);
    }

    private void persistSettlementPlan(ExpenseGroup group, Long groupId, List<SettlementLine> settlementPlan) {
        Set<String> activeSettlements = new HashSet<>();

        for (SettlementLine line : settlementPlan) {
            Long fromId = line.fromUserId();
            Long toId = line.toUserId();
            BigDecimal amount = line.amount();

            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                Settlement existing = findOpenSettlement(groupId, fromId, toId);
                if (existing != null) {
                    settlementRepository.delete(existing);
                    cleanupExtraOpenSettlements(groupId, fromId, toId, null);
                }
                continue;
            }

            activeSettlements.add(fromId + ":" + toId);

            Settlement sameOpen = findOpenSettlement(groupId, fromId, toId);
            if (sameOpen != null) {
                sameOpen.setAmount(amount);
                settlementRepository.save(sameOpen);
                cleanupExtraOpenSettlements(groupId, fromId, toId, sameOpen);
                continue;
            }

            Settlement oppositeOpen = findOpenSettlement(groupId, toId, fromId);
            if (oppositeOpen != null) {
                settlementRepository.delete(oppositeOpen);

                Settlement replacement = new Settlement(group, userRepository.findById(fromId).orElseThrow(), userRepository.findById(toId).orElseThrow(), amount);
                replacement.setSettlementDate(null);
                settlementRepository.save(replacement);
                cleanupExtraOpenSettlements(groupId, fromId, toId, replacement);
                continue;
            }

            Settlement newSettlement = new Settlement(group, userRepository.findById(fromId).orElseThrow(), userRepository.findById(toId).orElseThrow(), amount);
            newSettlement.setSettlementDate(null);
            settlementRepository.save(newSettlement);
            cleanupExtraOpenSettlements(groupId, fromId, toId, newSettlement);
        }

        deleteUnusedOpenSettlements(groupId, activeSettlements);
    }

    private Settlement findOpenSettlement(Long groupId, Long fromUserId, Long toUserId) {
        return settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(groupId, fromUserId, toUserId)
                .stream()
                .filter(settlement -> settlement.getSettlementDate() == null)
                .findFirst()
                .orElse(null);
    }

    private void cleanupExtraOpenSettlements(Long groupId, Long fromUserId, Long toUserId, Settlement keptSettlement) {
        settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(groupId, fromUserId, toUserId)
                .stream()
                .filter(settlement -> settlement.getSettlementDate() == null)
                .filter(settlement -> !settlement.equals(keptSettlement))
                .forEach(settlementRepository::delete);
    }

    private void deleteUnusedOpenSettlements(Long groupId, Set<String> activeSettlementKeys) {
        settlementRepository.findByGroupId(groupId).stream()
                .filter(settlement -> settlement.getSettlementDate() == null)
                .filter(settlement -> settlement.getAmount() == null || settlement.getAmount().compareTo(BigDecimal.ZERO) == 0
                        || !activeSettlementKeys.contains(settlement.getFromUser().getId() + ":" + settlement.getToUser().getId()))
                .forEach(settlementRepository::delete);
    }

    private ExpenseGroup requireMemberGroup(Long groupId, Long currentUserId) {
        return groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);
    }

}
