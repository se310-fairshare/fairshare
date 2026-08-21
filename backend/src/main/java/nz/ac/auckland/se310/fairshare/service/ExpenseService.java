package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
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

    public ExpenseService(ExpenseRepository expenseRepository, ExpenseGroupRepository groupRepository) {
        this.expenseRepository = expenseRepository;
        this.groupRepository = groupRepository;
    }

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request, Long currentUserId) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC8

        UserInGroup payer = group.getMember(request.paidByUserId());
        if (payer == null) {
            throw new InvalidPayerException(request.paidByUserId()); // AC5
        }

        BigDecimal amount = request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        LocalDate expenseDate = request.expenseDate() != null ? request.expenseDate() : LocalDate.now(); // AC6

        Expense expense = new Expense(
                group, payer.getUser(), amount, request.description().trim(), expenseDate);
        Expense saved = expenseRepository.save(expense);

        applyEqualSplit(group, payer, amount); // AC1, AC4

        return toResponse(saved);
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

    /**
     * AC4: the amount is split equally across every current member. Cents left over by an
     * uneven division go to the lowest user ids, so the shares always add back up to the
     * amount the payer actually spent.
     */
    private void applyEqualSplit(ExpenseGroup group, UserInGroup payer, BigDecimal amount) {
        List<UserInGroup> members = group.getMembers().stream()
                .sorted(Comparator.comparing(member -> member.getUser().getId()))
                .toList();

        long totalCents = amount.movePointRight(MONEY_SCALE).longValueExact();
        long baseShare = totalCents / members.size();
        long extraCents = totalCents % members.size();

        payer.adjustNetBalance(amount);

        for (int i = 0; i < members.size(); i++) {
            long shareCents = baseShare + (i < extraCents ? 1 : 0);
            members.get(i).adjustNetBalance(cents(-shareCents));
        }
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
                expense.getCreatedAt());
    }
}
