package com.natalio.aifinance.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.natalio.aifinance.user.AppUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void dashboardRequiresAuthenticationAndReturnsOnlyTheSignedInUsersStoredData() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
            .andExpect(status().isUnauthorized());

        String firstToken = registerAndGetToken("first_user");
        UUID firstUserId = users.findByUsernameIgnoreCase("first_user").orElseThrow().getId();
        addFinancialData(firstUserId, "Conta da Ana", "Alimentação", "Supermercado", "Casa");

        MvcResult firstDashboard = mockMvc.perform(get("/api/v1/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode firstBody = objectMapper.readTree(firstDashboard.getResponse().getContentAsString());

        assertEquals("first_user", firstBody.at("/summary/displayName").asText());
        assertEquals(6, firstBody.get("cashFlow").size());
        assertEquals(0, firstBody.at("/summary/netWorth").decimalValue().compareTo(new BigDecimal("2500.00")));
        assertEquals(300, firstBody.at("/summary/monthlyIncome").intValue());
        assertEquals(100, firstBody.at("/summary/totalSpent").intValue());
        assertEquals("Alimentação", firstBody.at("/spending/0/name").asText());
        assertEquals(2, firstBody.get("transactions").size());
        assertEquals("Casa", firstBody.at("/goals/0/name").asText());

        String secondToken = registerAndGetToken("second_user");
        MvcResult secondDashboard = mockMvc.perform(get("/api/v1/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode secondBody = objectMapper.readTree(secondDashboard.getResponse().getContentAsString());

        assertEquals(0, secondBody.at("/summary/netWorth").intValue());
        assertEquals(0, secondBody.at("/summary/monthlyIncome").intValue());
        assertEquals(0, secondBody.get("transactions").size());
        assertEquals(0, secondBody.get("goals").size());
    }

    @Test
    void dashboardFiltersAllTransactionSummariesByCustomDateRange() throws Exception {
        String token = registerAndGetToken("dates_user");
        UUID userId = users.findByUsernameIgnoreCase("dates_user").orElseThrow().getId();
        UUID accountId = UUID.randomUUID();
        UUID expenseCategoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_account (id, user_id, name, account_type, currency, current_balance)
                VALUES (?, ?, 'Conta de datas', 'CHECKING', 'EUR', 30.00)
                """, accountId, userId);
        jdbc.update("""
                INSERT INTO category (id, user_id, name, kind)
                VALUES (?, ?, 'Compras', 'EXPENSE')
                """, expenseCategoryId, userId);
        jdbc.update("""
                INSERT INTO financial_transaction
                    (id, user_id, account_id, category_id, source, description, amount, currency, booked_at)
                VALUES (?, ?, ?, ?, 'MANUAL', 'Dentro do período', -10.00, 'EUR', '2025-01-10')
                """, UUID.randomUUID(), userId, accountId, expenseCategoryId);
        jdbc.update("""
                INSERT INTO financial_transaction
                    (id, user_id, account_id, category_id, source, description, amount, currency, booked_at)
                VALUES (?, ?, ?, ?, 'MANUAL', 'Fora do período', -20.00, 'EUR', '2025-01-11')
                """, UUID.randomUUID(), userId, accountId, expenseCategoryId);

        MvcResult result = mockMvc.perform(get("/api/v1/dashboard")
                .queryParam("from", "2025-01-10")
                .queryParam("to", "2025-01-10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals(10, body.at("/summary/totalSpent").intValue());
        assertEquals(1, body.get("transactions").size());
        assertEquals("Dentro do período", body.at("/transactions/0/description").asText());
        assertEquals(1, body.get("cashFlow").size());

        mockMvc.perform(get("/api/v1/dashboard")
                .queryParam("from", "2025-01-10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/dashboard")
                .queryParam("from", "2019-01-01")
                .queryParam("to", "2025-01-02")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"A-long-password-123"}
                    """.formatted(username)))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void addFinancialData(UUID userId, String accountName, String categoryName, String description, String goalName) {
        UUID accountId = UUID.randomUUID();
        UUID incomeCategoryId = UUID.randomUUID();
        UUID expenseCategoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_account (id, user_id, name, account_type, currency, current_balance)
                VALUES (?, ?, ?, 'CHECKING', 'EUR', ?)
                """, accountId, userId, accountName, new BigDecimal("2500.00"));
        jdbc.update("""
                INSERT INTO category (id, user_id, name, kind)
                VALUES (?, ?, 'Salário', 'INCOME')
                """, incomeCategoryId, userId);
        jdbc.update("""
                INSERT INTO category (id, user_id, name, kind)
                VALUES (?, ?, ?, 'EXPENSE')
                """, expenseCategoryId, userId, categoryName);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO financial_transaction
                    (id, user_id, account_id, category_id, source, description, amount, currency, booked_at)
                VALUES (?, ?, ?, ?, 'MANUAL', 'Salário', ?, 'EUR', ?)
                """, UUID.randomUUID(), userId, accountId, incomeCategoryId, new BigDecimal("300.00"), today);
        jdbc.update("""
                INSERT INTO financial_transaction
                    (id, user_id, account_id, category_id, source, description, amount, currency, booked_at)
                VALUES (?, ?, ?, ?, 'MANUAL', ?, ?, 'EUR', ?)
                """, UUID.randomUUID(), userId, accountId, expenseCategoryId, description, new BigDecimal("-100.00"), today);
        jdbc.update("""
                INSERT INTO savings_goal (id, user_id, name, target_amount, current_amount, currency)
                VALUES (?, ?, ?, ?, ?, 'EUR')
                """, UUID.randomUUID(), userId, goalName, new BigDecimal("1000.00"), new BigDecimal("250.00"));
    }
}
