package nz.ac.auckland.se310.fairshare.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be a positive number")
        // Amounts are stored to the cent, so anything under one cent would round away to 0.00.
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        BigDecimal amount,

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Payer is required")
        Long paidByUserId,

        @PastOrPresent(message = "Expense date cannot be in the future")
        LocalDate expenseDate) {}
