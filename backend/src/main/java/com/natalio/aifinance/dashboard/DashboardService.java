package com.natalio.aifinance.dashboard;

import com.natalio.aifinance.dashboard.DashboardResponse.CashFlowMonth;
import com.natalio.aifinance.dashboard.DashboardResponse.SavingsGoal;
import com.natalio.aifinance.dashboard.DashboardResponse.SpendingCategory;
import com.natalio.aifinance.dashboard.DashboardResponse.Summary;
import com.natalio.aifinance.dashboard.DashboardResponse.TransactionItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class DashboardService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID userId) {
        UserRow user = jdbc.query("""
                SELECT display_name, rtrim(base_currency) AS currency
                FROM app_user
                WHERE id = ?
                """,
            (result, rowNum) -> new UserRow(result.getString("display_name"), result.getString("currency")),
            userId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        LocalDate rangeStart = currentMonth.minusMonths(5).atDay(1);
        LocalDate rangeEnd = currentMonth.plusMonths(1).atDay(1);
        Map<YearMonth, MonthTotals> totalsByMonth = new LinkedHashMap<>();
        for (int monthOffset = 5; monthOffset >= 0; monthOffset--) {
            totalsByMonth.put(currentMonth.minusMonths(monthOffset), new MonthTotals());
        }

        jdbc.query("""
                SELECT CAST(date_trunc('month', t.booked_at) AS DATE) AS month_start,
                       c.kind,
                       SUM(ABS(t.amount)) AS amount
                FROM financial_transaction t
                LEFT JOIN category c ON c.id = t.category_id AND (c.user_id = t.user_id OR c.user_id IS NULL)
                WHERE t.user_id = ? AND t.booked_at >= ? AND t.booked_at < ?
                  AND c.kind IN ('INCOME', 'EXPENSE')
                GROUP BY CAST(date_trunc('month', t.booked_at) AS DATE), c.kind
                """,
            result -> {
                YearMonth month = YearMonth.from(result.getDate("month_start").toLocalDate());
                MonthTotals totals = totalsByMonth.get(month);
                if (totals != null) {
                    BigDecimal amount = result.getBigDecimal("amount");
                    if ("INCOME".equals(result.getString("kind"))) {
                        totals.income = amount;
                    } else {
                        totals.expenses = amount;
                    }
                }
            },
            userId, rangeStart, rangeEnd
        );

        List<CashFlowMonth> cashFlow = new ArrayList<>();
        totalsByMonth.forEach((month, totals) -> cashFlow.add(new CashFlowMonth(
            month.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pt-PT")),
            totals.income,
            totals.expenses
        )));

        MonthTotals currentTotals = totalsByMonth.get(currentMonth);
        List<SpendingCategory> spending = jdbc.query("""
                SELECT c.name, SUM(ABS(t.amount)) AS amount
                FROM financial_transaction t
                JOIN category c ON c.id = t.category_id
                WHERE t.user_id = ? AND c.kind = 'EXPENSE'
                  AND t.booked_at >= ? AND t.booked_at < ?
                GROUP BY c.name
                ORDER BY amount DESC
                """,
            (result, rowNum) -> {
                BigDecimal amount = result.getBigDecimal("amount");
                BigDecimal percentage = currentTotals.expenses.signum() == 0
                    ? ZERO
                    : amount.multiply(BigDecimal.valueOf(100))
                        .divide(currentTotals.expenses, 1, RoundingMode.HALF_UP);
                return new SpendingCategory(result.getString("name"), amount, percentage);
            },
            userId, currentMonth.atDay(1), currentMonth.plusMonths(1).atDay(1)
        );

        BigDecimal netWorth = jdbc.queryForObject("""
                SELECT COALESCE(SUM(current_balance), 0)
                FROM financial_account
                WHERE user_id = ? AND is_active = true AND currency = ?
                """, BigDecimal.class, userId, user.currency);

        BigDecimal savingsRate = currentTotals.income.signum() == 0
            ? ZERO
            : currentTotals.income.subtract(currentTotals.expenses)
                .multiply(BigDecimal.valueOf(100))
                .divide(currentTotals.income, 1, RoundingMode.HALF_UP);

        List<TransactionItem> transactions = jdbc.query("""
                SELECT t.description, t.merchant_name, c.name AS category, c.kind,
                       t.amount, rtrim(t.currency) AS currency, t.booked_at
                FROM financial_transaction t
                LEFT JOIN category c ON c.id = t.category_id AND (c.user_id = t.user_id OR c.user_id IS NULL)
                WHERE t.user_id = ?
                ORDER BY t.booked_at DESC, t.created_at DESC
                LIMIT 50
                """,
            (result, rowNum) -> new TransactionItem(
                result.getString("description"),
                result.getString("merchant_name"),
                result.getString("category"),
                result.getString("kind"),
                result.getBigDecimal("amount"),
                result.getString("currency"),
                result.getDate("booked_at").toLocalDate()
            ),
            userId
        );

        List<SavingsGoal> goals = jdbc.query("""
                SELECT name, current_amount, target_amount, rtrim(currency) AS currency, target_date
                FROM savings_goal
                WHERE user_id = ?
                ORDER BY created_at
                """,
            (result, rowNum) -> new SavingsGoal(
                result.getString("name"),
                result.getBigDecimal("current_amount"),
                result.getBigDecimal("target_amount"),
                result.getString("currency"),
                result.getObject("target_date", LocalDate.class)
            ),
            userId
        );

        return new DashboardResponse(
            new Summary(
                user.displayName,
                user.currency,
                netWorth,
                currentTotals.income,
                currentTotals.expenses,
                savingsRate,
                BigDecimal.valueOf(50)
            ),
            cashFlow,
            spending,
            transactions,
            goals
        );
    }

    private record UserRow(String displayName, String currency) {
    }

    private static final class MonthTotals {
        private BigDecimal income = ZERO;
        private BigDecimal expenses = ZERO;
    }
}
