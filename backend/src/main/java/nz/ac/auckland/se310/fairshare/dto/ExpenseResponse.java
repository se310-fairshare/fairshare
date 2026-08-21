package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id, Long groupId, Long paidByUserId, String paidByUsername,
        BigDecimal amount, String description, LocalDate expenseDate, Instant createdAt) {}
