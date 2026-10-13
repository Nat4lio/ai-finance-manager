package com.natalio.aifinance.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
    Summary summary,
    List<CashFlowMonth> cashFlow,
    List<SpendingCategory> spending,
    List<TransactionItem> transactions,
    List<SavingsGoal> goals
) {
    public record Summary(
        String displayName,
        String currency,
        BigDecimal netWorth,
        BigDecimal monthlyIncome,
        BigDecimal totalSpent,
        BigDecimal savingsRate,
        BigDecimal savingsTarget
    ) {
    }

    public record CashFlowMonth(String month, BigDecimal income, BigDecimal expenses) {
    }

    public record SpendingCategory(String name, BigDecimal amount, BigDecimal percentage) {
    }

    public record TransactionItem(
        String description,
        String merchant,
        String category,
        String kind,
        BigDecimal amount,
        String currency,
        LocalDate date
    ) {
    }

    public record SavingsGoal(
        String name,
        BigDecimal currentAmount,
        BigDecimal targetAmount,
        String currency,
        LocalDate targetDate
    ) {
    }
}
