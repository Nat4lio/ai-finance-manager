package com.natalio.aifinance.transactions;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionService {
    private final JdbcTemplate jdbc;

    public TransactionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public TransactionCreatedResponse create(UUID userId, CreateTransactionRequest request) {
        AccountRow account;
        if (request.accountId() == null) {
            account = jdbc.query("""
                    SELECT id, rtrim(currency) AS currency
                    FROM financial_account
                    WHERE user_id = ? AND is_active = true
                    ORDER BY created_at, name
                    LIMIT 1
                    """,
                (result, rowNum) -> new AccountRow(
                    result.getObject("id", UUID.class),
                    result.getString("currency")
                ),
                userId
            ).stream().findFirst().orElseGet(() -> createDefaultAccount(userId));
        } else {
            account = jdbc.query("""
                    SELECT id, rtrim(currency) AS currency
                    FROM financial_account
                    WHERE id = ? AND user_id = ? AND is_active = true
                    """,
                (result, rowNum) -> new AccountRow(
                    result.getObject("id", UUID.class),
                    result.getString("currency")
                ),
                request.accountId(), userId
            ).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Active account not found"));
        }

        String categoryName = request.category().trim();
        UUID categoryId = jdbc.query("""
                SELECT id
                FROM category
                WHERE kind = ? AND lower(name) = lower(?)
                  AND (user_id = ? OR user_id IS NULL)
                ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """,
            (result, rowNum) -> result.getObject("id", UUID.class),
            request.kind().name(), categoryName, userId, userId
        ).stream().findFirst().orElseGet(() -> {
            UUID newCategoryId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO category (id, user_id, name, kind)
                    VALUES (?, ?, ?, ?)
                    """, newCategoryId, userId, categoryName, request.kind().name());
            return newCategoryId;
        });

        BigDecimal signedAmount = request.kind() == TransactionKind.INCOME
            ? request.amount()
            : request.amount().negate();
        UUID transactionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_transaction
                    (id, user_id, account_id, category_id, source, description, amount, currency, booked_at)
                VALUES (?, ?, ?, ?, 'MANUAL', ?, ?, ?, ?)
                """,
            transactionId,
            userId,
            account.id,
            categoryId,
            request.description().trim(),
            signedAmount,
            account.currency,
            request.date()
        );

        jdbc.update("""
                UPDATE financial_account
                SET current_balance = COALESCE(current_balance, 0) + ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ?
                """, signedAmount, account.id, userId);

        return new TransactionCreatedResponse(
            transactionId,
            request.description().trim(),
            categoryName,
            request.kind(),
            signedAmount,
            account.currency,
            request.date()
        );
    }

    private AccountRow createDefaultAccount(UUID userId) {
        String currency = jdbc.query("""
                SELECT rtrim(base_currency) AS currency
                FROM app_user
                WHERE id = ?
                """,
            (result, rowNum) -> result.getString("currency"),
            userId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_account (id, user_id, name, account_type, currency, current_balance)
                VALUES (?, ?, 'Conta principal', 'CHECKING', ?, 0)
                """, accountId, userId, currency);
        return new AccountRow(accountId, currency);
    }

    private record AccountRow(UUID id, String currency) {
    }
}
