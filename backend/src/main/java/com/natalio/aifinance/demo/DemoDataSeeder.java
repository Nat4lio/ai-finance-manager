package com.natalio.aifinance.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.natalio.aifinance.user.AppUser;
import com.natalio.aifinance.user.AppUserRepository;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_USERNAME = "demo";
    public static final String DEMO_PASSWORD = "Demo123!Finance";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(AppUserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppUser user = users.findByUsernameIgnoreCase(DEMO_USERNAME).orElseGet(() ->
            users.saveAndFlush(new AppUser(DEMO_USERNAME, passwordEncoder.encode(DEMO_PASSWORD))));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM financial_account WHERE user_id = ?", Integer.class, user.getId()) > 0) {
            return;
        }

        UUID checking = insertAccount(user.getId(), "Conta principal", "CHECKING", "61290.00");
        insertAccount(user.getId(), "Poupanças", "SAVINGS", "23000.00");

        Map<String, UUID> categories = new HashMap<>();
        categories.put("Salary", insertCategory(user.getId(), "Salário", "INCOME", "#10d7a4"));
        categories.put("Housing", insertCategory(user.getId(), "Casa", "EXPENSE", "#4da3ff"));
        categories.put("Food", insertCategory(user.getId(), "Alimentação", "EXPENSE", "#10d7a4"));
        categories.put("Transport", insertCategory(user.getId(), "Transportes", "EXPENSE", "#a78bfa"));
        categories.put("Entertainment", insertCategory(user.getId(), "Lazer", "EXPENSE", "#f4c95d"));
        categories.put("Other", insertCategory(user.getId(), "Outros", "EXPENSE", "#fb7185"));

        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        int[] incomes = {6200, 6280, 6410, 6520, 6680, 6840};
        int[] expenses = {2970, 3010, 3140, 3080, 3020, 3210};
        String[] merchants = {"Renda da casa", "Supermercado", "Passe mensal", "Subscrições", "Restaurantes"};
        int[] currentMonthCosts = {1200, 760, 580, 482, 188};
        String[] currentMonthCategories = {"Housing", "Food", "Transport", "Entertainment", "Other"};

        for (int offset = 5; offset >= 0; offset--) {
            YearMonth month = currentMonth.minusMonths(offset);
            LocalDate salaryDate = month.atDay(2);
            insertTransaction(user.getId(), checking, categories.get("Salary"), "Salário mensal",
                "Empresa", BigDecimal.valueOf(incomes[5 - offset]), salaryDate);

            if (offset == 0) {
                for (int i = 0; i < currentMonthCosts.length; i++) {
                    insertTransaction(user.getId(), checking, categories.get(currentMonthCategories[i]),
                        merchants[i], merchants[i], BigDecimal.valueOf(-currentMonthCosts[i]),
                        month.atDay(Math.min(4 + i * 4, month.lengthOfMonth())));
                }
            } else {
                int remaining = expenses[5 - offset];
                int[] weights = {38, 24, 18, 13};
                for (int i = 0; i < weights.length; i++) {
                    int cost = i == weights.length - 1 ? remaining : expenses[5 - offset] * weights[i] / 100;
                    remaining -= cost;
                    insertTransaction(user.getId(), checking, categories.get(currentMonthCategories[i]),
                        merchants[i], merchants[i], BigDecimal.valueOf(-cost),
                        month.atDay(Math.min(5 + i * 5, month.lengthOfMonth())));
                }
            }
        }

        jdbc.update("""
                INSERT INTO savings_goal (user_id, name, target_amount, current_amount, target_date, currency)
                VALUES (?, ?, ?, ?, ?, ?)
                """, user.getId(), "Entrada para casa", new BigDecimal("25000.00"),
            new BigDecimal("18400.00"), LocalDate.now(ZoneOffset.UTC).plusMonths(18), "EUR");
        jdbc.update("""
                INSERT INTO savings_goal (user_id, name, target_amount, current_amount, target_date, currency)
                VALUES (?, ?, ?, ?, ?, ?)
                """, user.getId(), "Viagem de verão", new BigDecimal("3000.00"),
            new BigDecimal("1320.00"), LocalDate.now(ZoneOffset.UTC).plusMonths(8), "EUR");
    }

    private UUID insertAccount(UUID userId, String name, String type, String balance) {
        return jdbc.queryForObject("""
                INSERT INTO financial_account (user_id, name, account_type, currency, current_balance)
                VALUES (?, ?, ?, 'EUR', ?)
                RETURNING id
                """, UUID.class, userId, name, type, new BigDecimal(balance));
    }

    private UUID insertCategory(UUID userId, String name, String kind, String color) {
        return jdbc.queryForObject("""
                INSERT INTO category (user_id, name, kind, color)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, UUID.class, userId, name, kind, color);
    }

    private void insertTransaction(
        UUID userId,
        UUID accountId,
        UUID categoryId,
        String description,
        String merchant,
        BigDecimal amount,
        LocalDate date
    ) {
        jdbc.update("""
                INSERT INTO financial_transaction
                    (user_id, account_id, category_id, source, description, merchant_name, amount, currency, booked_at)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'EUR', ?)
                """, userId, accountId, categoryId, description, merchant, amount, date);
    }
}
