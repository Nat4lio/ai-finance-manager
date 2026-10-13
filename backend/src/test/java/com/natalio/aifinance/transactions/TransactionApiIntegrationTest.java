package com.natalio.aifinance.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.natalio.aifinance.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionApiIntegrationTest {
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
    void recordsIncomeAndExpenseAndUpdatesBalanceAndDashboard() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(null, "INCOME", "Salário de outubro", "Salário", "125.50")))
            .andExpect(status().isUnauthorized());

        String token = registerAndGetToken("ana@example.com");
        MvcResult incomeResult = mockMvc.perform(post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(null, "INCOME", "Salário de outubro", "Salário", "125.50")))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode income = objectMapper.readTree(incomeResult.getResponse().getContentAsString());
        assertEquals("Salário de outubro", income.get("description").asText());
        assertEquals("Salário", income.get("category").asText());
        assertEquals("INCOME", income.get("kind").asText());
        assertEquals(0, income.get("amount").decimalValue().compareTo(new BigDecimal("125.50")));

        UUID userId = users.findByEmailIgnoreCase("ana@example.com").orElseThrow().getId();
        UUID accountId = jdbc.queryForObject(
            "SELECT id FROM financial_account WHERE user_id = ?",
            UUID.class,
            userId
        );
        assertNotNull(accountId);

        mockMvc.perform(post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(accountId, "EXPENSE", "Compra no supermercado", "Alimentação", "35.20")))
            .andExpect(status().isCreated());

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        MvcResult dashboardResult = mockMvc.perform(get("/api/v1/dashboard")
                .queryParam("from", today)
                .queryParam("to", today)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode dashboard = objectMapper.readTree(dashboardResult.getResponse().getContentAsString());

        assertEquals(0, dashboard.at("/summary/netWorth").decimalValue().compareTo(new BigDecimal("90.30")));
        assertEquals(0, dashboard.at("/summary/monthlyIncome").decimalValue().compareTo(new BigDecimal("125.50")));
        assertEquals(0, dashboard.at("/summary/totalSpent").decimalValue().compareTo(new BigDecimal("35.20")));
        assertEquals("Compra no supermercado", dashboard.at("/transactions/0/description").asText());
        assertEquals("Alimentação", dashboard.at("/transactions/0/category").asText());
        assertEquals(1, dashboard.at("/accounts").size());
    }

    @Test
    void rejectsInvalidAmountAndAnAccountOwnedByAnotherUser() throws Exception {
        String firstToken = registerAndGetToken("first@example.com");
        String secondToken = registerAndGetToken("second@example.com");
        UUID firstUserId = users.findByEmailIgnoreCase("first@example.com").orElseThrow().getId();
        UUID otherAccountId = insertAccount(firstUserId);
        UUID secondUserId = users.findByEmailIgnoreCase("second@example.com").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(otherAccountId, "INCOME", "Não autorizado", "Salário", "25.00")))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(null, "EXPENSE", "Valor inválido", "Lazer", "0")))
            .andExpect(status().isBadRequest());

        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_transaction WHERE user_id = ?",
            Integer.class,
            secondUserId
        ));
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_account WHERE user_id = ?",
            Integer.class,
            secondUserId
        ));
    }

    @Test
    void deletingTransactionReversesAccountBalanceAndIsRestrictedToItsOwner() throws Exception {
        String ownerToken = registerAndGetToken("transaction-owner@example.com");
        String otherToken = registerAndGetToken("transaction-other@example.com");

        MvcResult incomeResult = mockMvc.perform(post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(null, "INCOME", "Receita temporária", "Salário", "100.00")))
            .andExpect(status().isCreated())
            .andReturn();
        UUID incomeId = UUID.fromString(objectMapper.readTree(incomeResult.getResponse().getContentAsString())
            .get("id").asText());

        UUID userId = users.findByEmailIgnoreCase("transaction-owner@example.com").orElseThrow().getId();
        UUID accountId = jdbc.queryForObject(
            "SELECT id FROM financial_account WHERE user_id = ?",
            UUID.class,
            userId
        );
        MvcResult expenseResult = mockMvc.perform(post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(accountId, "EXPENSE", "Despesa temporária", "Lazer", "20.00")))
            .andExpect(status().isCreated())
            .andReturn();
        UUID expenseId = UUID.fromString(objectMapper.readTree(expenseResult.getResponse().getContentAsString())
            .get("id").asText());

        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", incomeId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", incomeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
            .andExpect(status().isNotFound());

        assertEquals(0, jdbc.queryForObject(
            "SELECT current_balance FROM financial_account WHERE id = ?",
            BigDecimal.class,
            accountId
        ).compareTo(new BigDecimal("80.00")));

        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", incomeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject(
            "SELECT current_balance FROM financial_account WHERE id = ?",
            BigDecimal.class,
            accountId
        ).compareTo(new BigDecimal("-20.00")));
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_transaction WHERE id = ?",
            Integer.class,
            incomeId
        ));

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        MvcResult dashboardResult = mockMvc.perform(get("/api/v1/dashboard")
                .queryParam("from", today)
                .queryParam("to", today)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode dashboard = objectMapper.readTree(dashboardResult.getResponse().getContentAsString());
        assertEquals(1, dashboard.get("transactions").size());
        assertEquals(expenseId.toString(), dashboard.at("/transactions/0/id").asText());

        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", incomeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", expenseId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject(
            "SELECT current_balance FROM financial_account WHERE id = ?",
            BigDecimal.class,
            accountId
        ).compareTo(BigDecimal.ZERO));
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_transaction WHERE user_id = ?",
            Integer.class,
            userId
        ));
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"A-long-password-123","displayName":"Ana"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID insertAccount(UUID userId) {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_account (id, user_id, name, account_type, currency, current_balance)
                VALUES (?, ?, 'Conta de teste', 'CHECKING', 'EUR', 100)
                """, accountId, userId);
        return accountId;
    }

    private String transactionJson(UUID accountId, String kind, String description, String category, String amount) {
        String account = accountId == null ? "null" : "\"" + accountId + "\"";
        return """
            {"accountId":%s,"kind":"%s","description":"%s","category":"%s","amount":%s,"date":"%s"}
            """.formatted(account, kind, description, category, amount, LocalDate.now(ZoneOffset.UTC));
    }
}
