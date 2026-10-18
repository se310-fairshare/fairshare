package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.Settlement;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Reconstructs balances from supplied history without changing it or accessing persistence. */
public class GroupBalanceCalculator {

    /**
     * Replays the group's historical expenses and past settlements to compute each member's current
     * net balance without relying on persisted per-member state alone.
     */
    public Map<Long, BigDecimal> calculate(ExpenseGroup group, List<ExpenseResponse> groupExpenses, List<Settlement> settlements) {
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
        for (Settlement settlement : settlements) {
            if (settlement.getSettlementDate() == null) {
                continue;
            }
            Long fromId = settlement.getFromUser().getId();
            Long toId = settlement.getToUser().getId();
            balances.merge(fromId, settlement.getAmount().negate(), BigDecimal::add);
            balances.merge(toId, settlement.getAmount(), BigDecimal::add);
        }

        balances.replaceAll((key, value) -> value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP));

        return balances;
    }

}
