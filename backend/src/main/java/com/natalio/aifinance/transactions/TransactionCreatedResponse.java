package com.natalio.aifinance.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionCreatedResponse(
    UUID id,
    String description,
    String category,
    TransactionKind kind,
    BigDecimal amount,
    String currency,
    LocalDate date
) {
}
