package com.natalio.aifinance.goals;

import com.natalio.aifinance.dashboard.DashboardResponse.SavingsGoal;
import com.natalio.aifinance.transactions.CreateTransactionRequest;
import com.natalio.aifinance.transactions.TransactionKind;
import com.natalio.aifinance.transactions.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoalService {
    private final JdbcTemplate jdbc;
    private final TransactionService transactionService;

    public GoalService(JdbcTemplate jdbc, TransactionService transactionService) {
        this.jdbc = jdbc;
        this.transactionService = transactionService;
    }

    @Transactional
    public SavingsGoal create(UUID userId, CreateGoalRequest request) {
        String currency = jdbc.query("""
                SELECT rtrim(base_currency) AS currency
                FROM app_user
                WHERE id = ?
                """,
            (result, rowNum) -> result.getString("currency"),
            userId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String name = request.name().trim();
        UUID goalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO savings_goal (id, user_id, name, target_amount, current_amount, target_date, currency)
                VALUES (?, ?, ?, ?, 0, ?, ?)
                """, goalId, userId, name, request.targetAmount(), request.targetDate(), currency);
        return new SavingsGoal(goalId, name, BigDecimal.ZERO, request.targetAmount(), currency, request.targetDate());
    }

    @Transactional
    public void delete(UUID userId, UUID goalId) {
        GoalRow goal = findGoal(userId, goalId);
        if (goal.currentAmount.signum() > 0) {
            AccountRow account = findOrCreateAccount(userId, goal.currency);
            transactionService.create(userId, new CreateTransactionRequest(
                account.id,
                TransactionKind.INCOME,
                "Devolução do objetivo: " + goal.name,
                "Devolução de poupança",
                goal.currentAmount,
                LocalDate.now(ZoneOffset.UTC)
            ));
        }
        jdbc.update("DELETE FROM savings_goal WHERE id = ? AND user_id = ?", goalId, userId);
    }

    @Transactional
    public SavingsGoal moveMoney(UUID userId, UUID goalId, GoalMovementRequest request) {
        GoalRow goal = findGoal(userId, goalId);

        boolean adding = request.kind() == GoalMovementKind.ADD;
        if (!adding && request.amount().compareTo(goal.currentAmount) > 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot withdraw more than the amount saved in this goal"
            );
        }
        BigDecimal updatedAmount = adding
            ? goal.currentAmount.add(request.amount())
            : goal.currentAmount.subtract(request.amount());
        AccountRow account = findOrCreateAccount(userId, goal.currency);
        TransactionKind transactionKind = adding ? TransactionKind.EXPENSE : TransactionKind.INCOME;
        String category = adding ? "Poupança" : "Levantamento de poupança";
        String description = (adding ? "Contribuição para objetivo: " : "Levantamento do objetivo: ") + goal.name;

        transactionService.createGoalMovement(userId, goalId, new CreateTransactionRequest(
            account.id,
            transactionKind,
            description,
            category,
            request.amount(),
            LocalDate.now(ZoneOffset.UTC)
        ));

        jdbc.update("""
                UPDATE savings_goal
                SET current_amount = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ?
                """, updatedAmount, goalId, userId);

        return new SavingsGoal(goalId, goal.name, updatedAmount, goal.targetAmount, goal.currency, goal.targetDate);
    }

    private GoalRow findGoal(UUID userId, UUID goalId) {
        return jdbc.query("""
                SELECT name, current_amount, target_amount, rtrim(currency) AS currency, target_date
                FROM savings_goal
                WHERE id = ? AND user_id = ?
                FOR UPDATE
                """,
            (result, rowNum) -> new GoalRow(
                result.getString("name"),
                result.getBigDecimal("current_amount"),
                result.getBigDecimal("target_amount"),
                result.getString("currency"),
                result.getObject("target_date", LocalDate.class)
            ),
            goalId, userId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings goal not found"));
    }

    private AccountRow findOrCreateAccount(UUID userId, String currency) {
        AccountRow account = jdbc.query("""
                SELECT id, rtrim(currency) AS currency
                FROM financial_account
                WHERE user_id = ? AND currency = ? AND is_active = true
                ORDER BY created_at, name
                LIMIT 1
                FOR UPDATE
                """,
            (result, rowNum) -> new AccountRow(
                result.getObject("id", UUID.class),
                result.getString("currency")
            ),
            userId, currency
        ).stream().findFirst().orElse(null);
        if (account != null) {
            return account;
        }

        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_account (id, user_id, name, account_type, currency, current_balance)
                VALUES (?, ?, 'Conta principal', 'CHECKING', ?, 0)
                """, accountId, userId, currency);
        return new AccountRow(accountId, currency);
    }

    private record GoalRow(
        String name,
        BigDecimal currentAmount,
        BigDecimal targetAmount,
        String currency,
        LocalDate targetDate
    ) {
    }

    private record AccountRow(UUID id, String currency) {
    }
}
