package nz.ac.auckland.se310.fairshare.service;


import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.*;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberConflictException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.GroupNotFoundException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.Settlement;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class ExpenseGroupService {

    private static class PersonBalance {
        Long name;
        BigDecimal amount;

        PersonBalance(Long name, BigDecimal amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    private final ExpenseGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final SettlementRepository settlementRepository;

    public ExpenseGroupService(ExpenseGroupRepository groupRepository, UserRepository userRepository, ExpenseService expenseService, SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.settlementRepository = settlementRepository;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + creatorId));

        ExpenseGroup group = new ExpenseGroup(
                request.name().trim(),
                request.description(),
                creator.getCurrency(),   // AC: base currency defaults from the creator
                creator);

        return toResponse(groupRepository.save(group));
    }

    // Returns the current user's groups in newest-first order, which matches the usual dashboard flow.
    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsForUser(Long userId) {
        return groupRepository.findByMembersUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long groupId, Long userId) {
        return groupRepository.findByIdAndMembersUserId(groupId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new GroupNotFoundException(groupId));  // AC8
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        Map<Long, BigDecimal> memberBalances = computeEffectiveBalances(groupId, group, expenseService.getExpensesForGroup(groupId, currentUserId));
        return group.getMembers().stream()
                .map(member -> toMemberResponse(member, currentUserId, memberBalances))
                .sorted((first, second) -> first.username().compareToIgnoreCase(second.username()))
                .toList();
    }


    @Transactional
    public GroupMemberResponse addMember(Long groupId, String identifier, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        User user = findUser(identifier.trim());

        if (group.hasMember(user.getId())) {
            throw new GroupMemberConflictException("User is already a member of this group");
        }

        group.addMember(user);
        groupRepository.save(group);
        Map<Long, BigDecimal> memberBalances = computeEffectiveBalances(group.getId(), group, expenseService.getExpensesForGroup(group.getId(), currentUserId));
        return toMemberResponse(group.getMember(user.getId()), currentUserId, memberBalances);
    }

    @Transactional
    public void removeMember(Long groupId, Long memberUserId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        UserInGroup member = group.getMember(memberUserId);

        if (member == null) {
            throw new GroupMemberNotFoundException("Group member not found");
        }
        if (group.getMembers().size() == 1) {
            throw new GroupMemberConflictException("A group must have at least one member");
        }
        if (member.hasOutstandingBalance()) {
            throw new GroupMemberConflictException(
                    "The member's balance must be settled before removal");
        }

        group.removeMember(member);
        groupRepository.save(group);
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

    public List<SettlementLine> computeSettlement(Long groupId, Long currentUserId, SettlementRequest request) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);

        Map<Long, BigDecimal> effectiveBalances = computeEffectiveBalances(groupId, group, expenseService.getExpensesForGroup(groupId, currentUserId));
        List<SettlementLine> settlementPlan = calculateSettlements(effectiveBalances);
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

    /**
     * Replays the group's historical expenses and past settlements to compute each member's current
     * net balance without relying on persisted per-member state alone.
     */
    private Map<Long, BigDecimal> computeEffectiveBalances(Long groupId, ExpenseGroup group, List<ExpenseResponse> groupExpenses) {
        // Start from a zero balance for each member and then replay the group's expense history to derive the live net position.
        Map<Long, BigDecimal> balances = new HashMap<>();

        // Reconstruct from supplied expenses (used when computing a settlement plan)
        for (UserInGroup member : group.getMembers()) {
            balances.put(member.getUser().getId(), BigDecimal.ZERO);
        }

        List<ExpenseResponse> expenses = groupExpenses == null ? Collections.emptyList() : groupExpenses;
        for (ExpenseResponse expense : expenses) {
            if (expense == null) {
                continue;
            }

            List<Long> participantIds = expense.participantUserIds() == null || expense.participantUserIds().isEmpty()
                    ? group.getMembers().stream().map(member -> member.getUser().getId()).sorted().toList()
                    : expense.participantUserIds().stream().distinct().sorted().toList();
            if (participantIds.isEmpty()) {
                continue;
            }

            BigDecimal expenseAmount = expense.amount() == null ? BigDecimal.ZERO : expense.amount().setScale(2, RoundingMode.HALF_UP);

            // Reconstruct cent-accurate split
            long totalCents = expenseAmount.movePointRight(2).longValueExact();
            long baseShare = totalCents / participantIds.size();
            long extraCents = totalCents % participantIds.size();

            if (expense.paidByUserId() != null) {
                balances.merge(expense.paidByUserId(), expenseAmount.negate(), BigDecimal::add);
            }

            for (int i = 0; i < participantIds.size(); i++) {
                long shareCents = baseShare + (i < extraCents ? 1 : 0);
                BigDecimal share = BigDecimal.valueOf(shareCents, 2);
                balances.merge(participantIds.get(i), share, BigDecimal::add);
            }
        }

        // Apply settlements on top of the expense totals. Open payments still represent a live debt,
        // while paid settlements reverse the earlier debt direction to keep the net balances accurate.
        for (Settlement settlement : settlementRepository.findByGroupId(groupId)) {
            if (settlement.getSettlementDate() == null) {
                continue;
            }
            Long fromId = settlement.getFromUser().getId();
            Long toId = settlement.getToUser().getId();
            if (settlement.getSettlementDate() == null) {
                balances.merge(fromId, settlement.getAmount(), BigDecimal::add);
                balances.merge(toId, settlement.getAmount().negate(), BigDecimal::add);
            } else {
                balances.merge(fromId, settlement.getAmount().negate(), BigDecimal::add);
                balances.merge(toId, settlement.getAmount(), BigDecimal::add);
            }
        }

        balances.replaceAll((key, value) -> value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP));

        return balances;
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

    public static List<SettlementLine> calculateSettlements(Map<Long, BigDecimal> totalExpenses) {
        if (totalExpenses == null || totalExpenses.isEmpty()) {
            return Collections.emptyList();
        }

        List<PersonBalance> active = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : totalExpenses.entrySet()) {
            BigDecimal amount = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue().setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                active.add(new PersonBalance(entry.getKey(), amount));
            }
        }

        int n = active.size();
        if (n == 0) return Collections.emptyList();

        // 1. Precalculate balances for all 2^n subsets
        BigDecimal[] subsetSums = new BigDecimal[1 << n];
        subsetSums[0] = BigDecimal.ZERO;
        for (int mask = 1; mask < (1 << n); mask++) {
            int lastBit = Integer.numberOfTrailingZeros(mask);
            subsetSums[mask] = subsetSums[mask ^ (1 << lastBit)].add(active.get(lastBit).amount);
        }

        // 2. Dynamic programming finds the largest zero-sum groups so the remaining balances can be settled efficiently.
        int[] dp = new int[1 << n];
        int[] parentMask = new int[1 << n];

        for (int mask = 1; mask < (1 << n); mask++) {
            dp[mask] = 0;
            parentMask[mask] = 0;

            // Try all submasks of 'mask'
            for (int submask = mask; submask > 0; submask = (submask - 1) & mask) {
                if (subsetSums[submask].compareTo(BigDecimal.ZERO) == 0) {
                    int val = 1 + dp[mask ^ submask];
                    if (val > dp[mask]) {
                        dp[mask] = val;
                        parentMask[mask] = submask;
                    }
                }
            }
        }

        // 3. Reconstruct zero-sum groups
        List<List<PersonBalance>> groups = new ArrayList<>();
        int curr = (1 << n) - 1;

        while (curr > 0) {
            int sub = parentMask[curr];
            if (sub == 0) {
                // Remaining elements don't form a smaller zero-sum submask; settle them as one group
                List<PersonBalance> group = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if ((curr & (1 << i)) != 0) {
                        group.add(new PersonBalance(active.get(i).name, active.get(i).amount));
                    }
                }
                groups.add(group);
                break;
            } else {
                List<PersonBalance> group = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if ((sub & (1 << i)) != 0) {
                        group.add(new PersonBalance(active.get(i).name, active.get(i).amount));
                    }
                }
                groups.add(group);
                curr ^= sub;
            }
        }

        // 4. Settle each zero-sum group greedy-style
        List<SettlementLine> transactions = new ArrayList<>();
        for (List<PersonBalance> group : groups) {
            transactions.addAll(settleGroup(group));
        }

        return transactions;
    }

    private static List<SettlementLine> settleGroup(List<PersonBalance> group) {
        List<PersonBalance> debtors = new ArrayList<>();
        List<PersonBalance> creditors = new ArrayList<>();

        for (PersonBalance p : group) {
            if (p.amount.compareTo(BigDecimal.ZERO) > 0) {
                debtors.add(new PersonBalance(p.name, p.amount));
            } else if (p.amount.compareTo(BigDecimal.ZERO) < 0) {
                creditors.add(new PersonBalance(p.name, p.amount.abs()));
            }
        }

        List<SettlementLine> txs = new ArrayList<>();
        int i = 0, j = 0;

        while (i < debtors.size() && j < creditors.size()) {
            PersonBalance debtor = debtors.get(i);
            PersonBalance creditor = creditors.get(j);

            BigDecimal payment = debtor.amount.min(creditor.amount);
            if (payment.compareTo(BigDecimal.ZERO) > 0) {
                // Using ID string representations; update if PersonBalance has a name field
                txs.add(new SettlementLine(debtor.name, creditor.name, payment));
            }

            debtor.amount = debtor.amount.subtract(payment);
            creditor.amount = creditor.amount.subtract(payment);

            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) i++;
            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) j++;
        }

        return txs;
    }

    private ExpenseGroup requireMemberGroup(Long groupId, Long currentUserId) {
        return groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);
    }

    private User findUser(String identifier) {
        return userRepository.findByEmailIgnoreCase(identifier)
                .orElseGet(() -> findUserByUsername(identifier));
    }

    private User findUserByUsername(String username) {
        List<User> matches = userRepository.findAllByUsernameIgnoreCase(username);
        if (matches.isEmpty()) {
            throw new GroupMemberNotFoundException("No matching user was found");
        }
        if (matches.size() > 1) {
            throw new GroupMemberConflictException(
                    "Multiple users match that username; use an email address");
        }
        return matches.getFirst();
    }

    private GroupResponse toResponse(ExpenseGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getGroupName(),
                group.getDescription(),
                group.getBaseCurrency().name(),
                group.getCreatedAt(),
                group.getMembers().size());
    }

    private GroupMemberResponse toMemberResponse(UserInGroup member, Long currentUserId, Map<Long, BigDecimal> memberBalances) {
        User user = member.getUser();
        return new GroupMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                memberBalances.getOrDefault(user.getId(), BigDecimal.ZERO),
                user.getId().equals(currentUserId));
    }

}
