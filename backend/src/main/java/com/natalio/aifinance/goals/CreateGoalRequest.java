package com.natalio.aifinance.goals;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoalRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal targetAmount,
    @FutureOrPresent LocalDate targetDate
) {
}
