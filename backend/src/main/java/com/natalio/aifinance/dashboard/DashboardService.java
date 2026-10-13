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
    public DashboardResponse getDashboard(UUID userId, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate rangeStart = requestedFrom == null && requestedTo == null
            ? YearMonth.from(today).minusMonths(5).atDay(1)
            : requestedFrom;
        LocalDate rangeEnd = requestedTo == null && requestedFrom == null ? today : requestedTo;
        if (rangeStart == null || rangeEnd == null || rangeStart.isAfter(rangeEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid start and end date are required");
        }
        if (rangeStart.isBefore(rangeEnd.minusYears(5))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range cannot exceed five years");
        }
        UserRow user = jdbc.query("""
                SELECT display_name, rtrim(base_currency) AS currency
                FROM app_user
                WHERE id = ?
                """,
            (result, rowNum) -> new UserRow(result.getString("display_name"), result.getString("currency")),
            userId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Map<YearMonth, MonthTotals> totalsByMonth = new LinkedHashMap<>();
        YearMonth firstMonth = YearMonth.from(rangeStart);
        YearMonth lastMonth = YearMonth.from(rangeEnd);
        for (YearMonth month = firstMonth; !month.isAfter(lastMonth); month = month.plusMonths(1)) {
            totalsByMonth.put(month, new MonthTotals());
        }

        jdbc.query("""
                SELECT CAST(date_trunc('month', t.booked_at) AS DATE) AS month_start,
                       c.kind,
                       SUM(ABS(t.amount)) AS amount
                FROM financial_transaction t
                LEFT JOIN category c ON c.id = t.category_id AND (c.user_id = t.user_id OR c.user_id IS NULL)
                WHERE t.user_id = ? AND t.booked_at >= ? AND t.booked_at <= ?
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
        boolean includeYear = firstMonth.until(lastMonth, java.time.temporal.ChronoUnit.MONTHS) >= 12;
        totalsByMonth.forEach((month, totals) -> cashFlow.add(new CashFlowMonth(
            month.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pt-PT"))
                + (includeYear ? " " + String.valueOf(month.getYear()).substring(2) : ""),
            totals.income,
            totals.expenses
        )));

        MonthTotals periodTotals = totalsByMonth.values().stream().reduce(new MonthTotals(), MonthTotals::add);
        List<DashboardResponse.AccountOption> accounts = jdbc.query("""
                SELECT id, name, rtrim(currency) AS currency
                FROM financial_account
                WHERE user_id = ? AND is_active = true
                ORDER BY created_at, name
                """,
            (result, rowNum) -> new DashboardResponse.AccountOption(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getString("currency")
            ),
            userId
        );
        List<SpendingCategory> spending = jdbc.query("""
                SELECT c.name, SUM(ABS(t.amount)) AS amount
                FROM financial_transaction t
                JOIN category c ON c.id = t.category_id
                WHERE t.user_id = ? AND c.kind = 'EXPENSE'
                  AND t.booked_at >= ? AND t.booked_at <= ?
                GROUP BY c.name
                ORDER BY amount DESC
                """,
            (result, rowNum) -> {
                BigDecimal amount = result.getBigDecimal("amount");
                BigDecimal percentage = periodTotals.expenses.signum() == 0
                    ? ZERO
                    : amount.multiply(BigDecimal.valueOf(100))
                        .divide(periodTotals.expenses, 1, RoundingMode.HALF_UP);
                return new SpendingCategory(result.getString("name"), amount, percentage);
            },
            userId, rangeStart, rangeEnd
        );

        BigDecimal netWorth = jdbc.queryForObject("""
                SELECT COALESCE(SUM(current_balance), 0)
                FROM financial_account
                WHERE user_id = ? AND is_active = true AND currency = ?
                """, BigDecimal.class, userId, user.currency);

        BigDecimal savingsRate = periodTotals.income.signum() == 0
            ? ZERO
            : periodTotals.income.subtract(periodTotals.expenses)
                .multiply(BigDecimal.valueOf(100))
                .divide(periodTotals.income, 1, RoundingMode.HALF_UP);

        List<TransactionItem> transactions = jdbc.query("""
                SELECT t.description, t.merchant_name, c.name AS category, c.kind,
                       t.amount, rtrim(t.currency) AS currency, t.booked_at
                FROM financial_transaction t
                LEFT JOIN category c ON c.id = t.category_id AND (c.user_id = t.user_id OR c.user_id IS NULL)
                WHERE t.user_id = ? AND t.booked_at >= ? AND t.booked_at <= ?
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
            userId, rangeStart, rangeEnd
        );

        List<SavingsGoal> goals = jdbc.query("""
                SELECT id, name, current_amount, target_amount, rtrim(currency) AS currency, target_date
                FROM savings_goal
                WHERE user_id = ?
                ORDER BY created_at
                """,
            (result, rowNum) -> new SavingsGoal(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getBigDecimal("current_amount"),
                result.getBigDecimal("target_amount"),
                result.getString("currency"),
                result.getObject("target_date", LocalDate.class)
            ),
            userId
        );

        return new DashboardResponse(
            rangeStart,
            rangeEnd,
            new Summary(
                user.displayName,
                user.currency,
                netWorth,
                periodTotals.income,
                periodTotals.expenses,
                savingsRate,
                BigDecimal.valueOf(50)
            ),
            accounts,
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

        private MonthTotals add(MonthTotals other) {
            income = income.add(other.income);
            expenses = expenses.add(other.expenses);
            return this;
        }
    }
}
