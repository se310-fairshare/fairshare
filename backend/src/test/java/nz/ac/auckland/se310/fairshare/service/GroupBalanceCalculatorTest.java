package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class GroupBalanceCalculatorTest {
    private final GroupBalanceCalculator calculator = new GroupBalanceCalculator();
    private final User alice = user(1L);
    private final User bob = user(2L);
    private final ExpenseGroup group = group();

    private static User user(Long id) {
        User user = new User("user" + id, "pw", id + "@test.com", User.Country.NEW_ZEALAND, User.Currency.NZD);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ExpenseGroup group() {
        ExpenseGroup result = new ExpenseGroup("Trip", null, User.Currency.NZD, alice);
        result.addMember(bob);
        result.addMember(user(3L));
        return result;
    }

    private ExpenseResponse expense(BigDecimal amount, List<Long> participants) {
        return new ExpenseResponse(1L, 42L, 1L, "alice", amount, null, null, null, participants);
    }

    @Test
    void emptyHistoryIgnoresStoredBalances() {
        group.getMember(1L).adjustNetBalance(new BigDecimal("100"));
        assertThat(calculator.calculate(group, null, List.of())).containsExactlyInAnyOrderEntriesOf(
                Map.of(1L, new BigDecimal("0.00"), 2L, new BigDecimal("0.00"), 3L, new BigDecimal("0.00")));
        assertThat(group.getMember(1L).getNetBalance()).isEqualByComparingTo("100");
    }

    @Test
    void missingParticipantsSplitAmongCurrentMembersWithRemainderById() {
        var balances = calculator.calculate(group, List.of(expense(new BigDecimal("10.00"), null)), List.of());
        assertThat(balances).containsEntry(1L, new BigDecimal("-6.66"))
                .containsEntry(2L, new BigDecimal("3.33")).containsEntry(3L, new BigDecimal("3.33"));
    }

    @Test
    void explicitParticipantsAreSortedAndDeduplicatedIncludingFormerMembers() {
        var balances = calculator.calculate(group,
                List.of(expense(new BigDecimal("10.01"), List.of(4L, 2L, 4L))), List.of());
        assertThat(balances).containsEntry(1L, new BigDecimal("-10.01"))
                .containsEntry(2L, new BigDecimal("5.01")).containsEntry(4L, new BigDecimal("5.00"))
                .containsEntry(3L, new BigDecimal("0.00"));
    }

    @Test
    void onlyPaidSettlementsReduceDebtWithoutMutatingHistory() {
        Settlement paid = new Settlement(group, bob, alice, new BigDecimal("2.00"));
        paid.setSettlementDate(LocalDate.of(2026, 1, 1));
        Settlement open = new Settlement(group, bob, alice, new BigDecimal("3.00"));
        open.setSettlementDate(null);
        var expenses = List.of(expense(new BigDecimal("10"), List.of(1L, 2L)));
        var settlements = List.of(paid, open);
        var balances = calculator.calculate(group, expenses, settlements);
        assertThat(balances).containsEntry(1L, new BigDecimal("-3.00"))
                .containsEntry(2L, new BigDecimal("3.00"));
        assertThat(calculator.calculate(group, expenses, settlements)).isEqualTo(balances);
        assertThat(paid.getAmount()).isEqualByComparingTo("2.00");
        assertThat(open.getSettlementDate()).isNull();
        assertThat(group.getMember(1L).getNetBalance()).isZero();
    }

    @Test
    void nullExpensesAndAmountsAreIgnoredAndAmountsRoundHalfUp() {
        var expenses = Arrays.asList(null, expense(null, List.of(1L, 2L)),
                expense(new BigDecimal("10.005"), List.of(2L)));
        assertThat(calculator.calculate(group, expenses, List.of()))
                .containsEntry(1L, new BigDecimal("-10.01")).containsEntry(2L, new BigDecimal("10.01"));
    }
}
