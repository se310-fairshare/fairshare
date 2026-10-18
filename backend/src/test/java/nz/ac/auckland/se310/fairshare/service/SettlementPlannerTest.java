package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.SettlementLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementPlannerTest {

    @Test
    void calculateSettlements_returnsEmptyForNullOrEmpty() {
        assertThat(SettlementPlanner.calculateSettlements(null)).isEmpty();
        assertThat(SettlementPlanner.calculateSettlements(new HashMap<>())).isEmpty();
    }

    @Test
    void calculateSettlements_simpleDebtorCreditorProducesOneLine() {
        Map<Long, BigDecimal> totals = new HashMap<>();
        totals.put(1L, new BigDecimal("10.00")); // debtor
        totals.put(2L, new BigDecimal("-10.00")); // creditor

        List<SettlementLine> lines = SettlementPlanner.calculateSettlements(totals);

        assertThat(lines).hasSize(1);
        SettlementLine line = lines.get(0);
        assertThat(line.fromUserId()).isEqualTo(1L);
        assertThat(line.toUserId()).isEqualTo(2L);
        assertThat(line.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void calculateSettlements_smallAmountsRoundingProducesExpectedTransactions() {
        Map<Long, BigDecimal> totals = new HashMap<>();
        // debtor owes a very small amount which will round to 0.01 when creating a payment
        totals.put(1L, new BigDecimal("0.009"));
        totals.put(2L, new BigDecimal("-0.009"));

        List<SettlementLine> lines = SettlementPlanner.calculateSettlements(totals);

        // rounding should produce a single 0.01 transaction
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).amount()).isEqualByComparingTo("0.01");
    }

    @Test
    void handleNullOrEmpty() {
        assertThat(SettlementPlanner.calculateSettlements(null)).isEmpty();
        assertThat(SettlementPlanner.calculateSettlements(Collections.emptyMap())).isEmpty();
    }

    @Test
    void handleAllZeroBalances() {
        Map<Long, BigDecimal> expenses = Map.of(
                1L, BigDecimal.ZERO,
                2L, new BigDecimal("0.00")
        );

        assertThat(SettlementPlanner.calculateSettlements(expenses)).isEmpty();
    }

    @Test
    void simpleTwoPersonSettlement() {
        // Person 1 owes 50, Person 2 is owed 50
        Map<Long, BigDecimal> expenses = Map.of(
                1L, new BigDecimal("50.00"),
                2L, new BigDecimal("-50.00")
        );

        List<SettlementLine> settlements = SettlementPlanner.calculateSettlements(expenses);

        assertThat(settlements).hasSize(1);
        assertThat(settlements.get(0).fromUserId()).isEqualTo(1L);
        assertThat(settlements.get(0).toUserId()).isEqualTo(2L);
        assertThat(settlements.get(0).amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void minimizeTransactionsWithZeroSumSubgroups() {
        /*
         * Subgroup A:
         * 1: +10.00 (Debtor)
         * 2: -10.00 (Creditor) -> Subgroup sum = 0 (1 tx)
         *
         * Subgroup B:
         * 3: +20.00 (Debtor)
         * 4: -20.00 (Creditor) -> Subgroup sum = 0 (1 tx)
         *
         * Total transactions should be 2, not 3.
         */
        Map<Long, BigDecimal> expenses = Map.of(
                1L, new BigDecimal("10.00"),
                2L, new BigDecimal("-10.00"),
                3L, new BigDecimal("20.00"),
                4L, new BigDecimal("-20.00")
        );

        List<SettlementLine> settlements = SettlementPlanner.calculateSettlements(expenses);

        assertThat(settlements).hasSize(2);

        // Verify balance conservation
        verifyBalancesCleared(expenses, settlements);
    }

    @Test
    void subsetSumOutperformsGreedy() {
        /*
         * Balances:
         * 1: +6.00
         * 2: +4.00
         * 3: -5.00
         * 4: -5.00
         *
         * Total balance = 0 across 4 people.
         * Subgroup 1: {1 (+6), 3 (-5), 4 (-5)} -> Wait, {1 (+6), 3 (-1), 4 (-5)}...
         * Better partition:
         * 1 (+6), 2 (+4), 3 (-5), 4 (-5) -> Sum = 0.
         * Optimal transactions: N - Subgroups = 4 - 1 = 3 transactions max.
         *
         * Subgroup split example:
         * 1 (+5.00), 2 (+5.00), 3 (-10.00) -> Sum = 0 (2 txs)
         * 4 (+3.00), 5 (-3.00)              -> Sum = 0 (1 tx)
         * Total = 3 transactions (greedy might pair +5 with -3 and yield 4 txs)
         */
        Map<Long, BigDecimal> expenses = Map.of(
                1L, new BigDecimal("5.00"),
                2L, new BigDecimal("5.00"),
                3L, new BigDecimal("-10.00"),
                4L, new BigDecimal("3.00"),
                5L, new BigDecimal("-3.00")
        );

        List<SettlementLine> settlements = SettlementPlanner.calculateSettlements(expenses);

        assertThat(settlements).hasSize(3);
        verifyBalancesCleared(expenses, settlements);
    }

    @Test
    void planningDoesNotMutateBalancesAndCanBeRepeated() {
        Map<Long, BigDecimal> balances = new java.util.LinkedHashMap<>();
        balances.put(1L, new BigDecimal("10.005"));
        balances.put(2L, new BigDecimal("-10.005"));
        balances.put(3L, null);
        Map<Long, BigDecimal> original = new HashMap<>(balances);
        var first = SettlementPlanner.calculateSettlements(balances);
        assertThat(first).containsExactly(new SettlementLine(1L, 2L, new BigDecimal("10.01")));
        assertThat(SettlementPlanner.calculateSettlements(balances)).isEqualTo(first);
        assertThat(balances).isEqualTo(original);
    }

    /**
     * Helper to verify that applying the transactions correctly clears all net balances to zero.
     */
    private void verifyBalancesCleared(Map<Long, BigDecimal> initialExpenses, List<SettlementLine> settlements) {
        Map<Long, BigDecimal> runningBalances = new HashMap<>(initialExpenses);

        for (SettlementLine line : settlements) {
            Long debtorId = line.fromUserId();
            Long creditorId = line.toUserId();
            BigDecimal amount = line.amount();

            // Debtor pays amount -> balance decreases
            runningBalances.put(debtorId, runningBalances.get(debtorId).subtract(amount));
            // Creditor receives amount -> balance increases (negative balance moves closer to zero)
            runningBalances.put(creditorId, runningBalances.get(creditorId).add(amount));
        }

        for (Map.Entry<Long, BigDecimal> entry : runningBalances.entrySet()) {
            assertThat(entry.getValue().stripTrailingZeros())
                    .withFailMessage("Person %d still has remaining balance: %s", entry.getKey(), entry.getValue())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
