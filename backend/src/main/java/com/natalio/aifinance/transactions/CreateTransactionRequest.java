package com.natalio.aifinance.transactions;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
    UUID accountId,
    @NotNull TransactionKind kind,
    @NotBlank @Size(max = 500) String description,
    @NotBlank @Size(max = 80) String category,
    @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
    @NotNull LocalDate date
) {
}
