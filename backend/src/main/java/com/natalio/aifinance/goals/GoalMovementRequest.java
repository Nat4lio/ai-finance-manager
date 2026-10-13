package com.natalio.aifinance.goals;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record GoalMovementRequest(
    @NotNull GoalMovementKind kind,
    @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount
) {
}
