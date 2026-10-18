package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.SettlementLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure settlement planning; positive balances owe money and negative balances are owed money. */
public class SettlementPlanner {

    private static class PersonBalance {
        Long name;
        BigDecimal amount;

        PersonBalance(Long name, BigDecimal amount) {
            this.name = name;
            this.amount = amount;
        }
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
                txs.add(new SettlementLine(debtor.name, creditor.name, payment));
            }

            debtor.amount = debtor.amount.subtract(payment);
            creditor.amount = creditor.amount.subtract(payment);

            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) i++;
            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) j++;
        }

        return txs;
    }

}
