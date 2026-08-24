package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.ExpenseNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class ExpenseService {

    private static final int MONEY_SCALE = 2;

    private final ExpenseRepository expenseRepository;
    private final ExpenseGroupRepository groupRepository;
    private final ExpenseShareRepository expenseShareRepository;


    public ExpenseService(ExpenseRepository expenseRepository, ExpenseGroupRepository groupRepository, ExpenseShareRepository expenseShareRepository) {
        this.expenseRepository = expenseRepository;
        this.groupRepository = groupRepository;
        this.expenseShareRepository = expenseShareRepository;
    }

    /**
     * Validates that the current user belongs to the group, confirms the payer is a member, and then
     * saves the expense and its equal-share breakdown for the selected participants.
     */
    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request, Long currentUserId) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC8

        UserInGroup payer = group.getMember(request.paidByUserId());
        if (payer == null) {
            throw new InvalidPayerException(request.paidByUserId()); // AC5
        }

        List<UserInGroup> members = request.participantUserIds().stream()
                .distinct() // duplicate IDs must not be counted more than once in the split
                .map(group::getMember)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(m -> m.getUser().getId()))
                .toList();
        if (members.isEmpty()) {
            throw new IllegalStateException("No valid participants found in the group for the expense");
        }

        BigDecimal amount = request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        LocalDate expenseDate = request.expenseDate() != null ? request.expenseDate() : LocalDate.now(); // AC6

        Expense expense = new Expense(
                group, payer.getUser(), amount, request.description().trim(), expenseDate);
        Expense saved = expenseRepository.save(expense);

        applyEqualSplit(payer, amount, members, saved); // AC1, AC4

        return toResponse(saved);
    }

    /**
     * Rewrites an existing expense by removing the old share allocation, applying the new payer and
     * participant list, and persisting the updated amount and metadata on the original expense.
     */
    @Transactional
    public void updateExpense(Long groupId, CreateExpenseRequest request, Long currentUserId, Long expenseId) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC8

        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(ExpenseNotFoundException::new);

        UserInGroup originalPayer = group.getMember(expense.getPaidBy().getId());
        UserInGroup payer = group.getMember(request.paidByUserId());
        if (payer == null) {
            throw new InvalidPayerException(request.paidByUserId());
        }

        // Rebuild the participant list from the current group membership so edits cannot use stale or invalid users.
        List<UserInGroup> members = request.participantUserIds().stream()
                .distinct() // duplicate IDs must not be counted more than once in the split
                .map(group::getMember)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(m -> m.getUser().getId()))
                .toList();
        if (members.isEmpty()) {
            throw new IllegalStateException("No valid participants found in the group for the expense");
        }

        BigDecimal amount = request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        updateSplit(expense, originalPayer, payer, amount, members);

        expense.setAmount(amount);
        expense.setDescription(request.description().trim());
        expense.setPaidBy(payer.getUser());
        if (request.expenseDate() != null) {
            expense.setExpenseDate(request.expenseDate());
        }

        expenseRepository.save(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesForGroup(Long groupId, Long currentUserId) {
        groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC8

        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long groupId, Long expenseId, Long currentUserId) {
        groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);

        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found in group"));

        return toResponse(expense);
    }

    /**
     * AC4: the amount is split equally across every current member. Cents left over by an
     * uneven division go to the lowest user ids, so the shares always add back up to the
     * amount the payer actually spent.
     */
    private void applyEqualSplit(UserInGroup payer, BigDecimal amount, List<UserInGroup> members, Expense expense) {
        long totalCents = amount.movePointRight(MONEY_SCALE).longValueExact();
        long baseShare = totalCents / members.size();
        long extraCents = totalCents % members.size();

        // Positive netBalance means the member owes money. Payer paid, so they are owed -> subtract
        payer.adjustNetBalance(amount.negate());

        for (int i = 0; i < members.size(); i++) {
            long shareCents = baseShare + (i < extraCents ? 1 : 0);
            // participants owe the share
            members.get(i).adjustNetBalance(cents(shareCents));
            try {
                expenseShareRepository.save(new ExpenseShare(members.get(i).getUser(), expense, cents(shareCents)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to save expense share for user " + members.get(i).getUser().getId(), e);
            }
        }
    }

    private void updateSplit(Expense expense, UserInGroup originalPayer, UserInGroup newPayer,
                             BigDecimal newAmount, List<UserInGroup> members) {
        // First, reverse the previous split
        List<ExpenseShare> existingShares = expenseShareRepository.findByExpenseId(expense.getId());
        for (ExpenseShare share : existingShares) {
            UserInGroup member = expense.getGroup().getMember(share.getUser().getId());
            if (member != null) {
                // previously participants had added positive share amounts; remove that
                member.adjustNetBalance(share.getShareAmount().negate());
            }
            expenseShareRepository.delete(share);
        }
        // Flush so the deletes hit the DB before the new shares are inserted, otherwise the
        // unique constraint on (user_id, expense_id) can be violated by Hibernate's action ordering.
        expenseShareRepository.flush();
        // Revert the original payer's owed/owed-to adjustment
        originalPayer.adjustNetBalance(expense.getAmount());

        // Apply the new split
        applyEqualSplit(newPayer, newAmount, members, expense);
    }

    private BigDecimal cents(long value) {
        return BigDecimal.valueOf(value, MONEY_SCALE);
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getPaidBy().getId(),
                expense.getPaidBy().getUsername(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getExpenseDate(),
                expense.getCreatedAt(),
                expenseShareRepository.findByExpenseId(expense.getId()).stream()
                    .map(share -> share.getUser().getId())
                    .sorted()
                    .toList());
    }
}
