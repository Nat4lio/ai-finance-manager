package com.natalio.aifinance.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.natalio.aifinance.user.AppUserRepository;
import java.math.BigDecimal;
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
class GoalApiIntegrationTest {
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
    void createsGoalMovesMoneyThroughAccountTransactionsAndDeletesOnlyOwnedGoals() throws Exception {
        mockMvc.perform(post("/api/v1/goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(goalJson("Férias", "1000.00")))
            .andExpect(status().isUnauthorized());

        String ownerToken = registerAndGetToken("owner@example.com");
        String otherToken = registerAndGetToken("other@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/goals")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(goalJson("Férias", "1000.00")))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode createdGoal = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID goalId = UUID.fromString(createdGoal.get("id").asText());
        assertEquals("Férias", createdGoal.get("name").asText());
        assertEquals(0, createdGoal.get("currentAmount").decimalValue().compareTo(BigDecimal.ZERO));

        mockMvc.perform(post("/api/v1/goals/{goalId}/movements", goalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"ADD","amount":150.00}
                    """))
            .andExpect(status().isOk());

        UUID ownerId = users.findByEmailIgnoreCase("owner@example.com").orElseThrow().getId();
        UUID accountId = jdbc.queryForObject(
            "SELECT id FROM financial_account WHERE user_id = ?",
            UUID.class,
            ownerId
        );
        assertEquals(0, jdbc.queryForObject(
            "SELECT current_balance FROM financial_account WHERE id = ?",
            BigDecimal.class,
            accountId
        ).compareTo(new BigDecimal("-150.00")));
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_transaction WHERE user_id = ? AND amount < 0",
            Integer.class,
            ownerId
        ));

        mockMvc.perform(post("/api/v1/goals/{goalId}/movements", goalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"WITHDRAW","amount":50.00}
                    """))
            .andExpect(status().isOk());

        assertEquals(0, jdbc.queryForObject(
            "SELECT current_amount FROM savings_goal WHERE id = ?",
            BigDecimal.class,
            goalId
        ).compareTo(new BigDecimal("100.00")));
        assertEquals(0, jdbc.queryForObject(
            "SELECT current_balance FROM financial_account WHERE id = ?",
            BigDecimal.class,
            accountId
        ).compareTo(new BigDecimal("-100.00")));
        assertEquals(2, jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_transaction WHERE user_id = ?",
            Integer.class,
            ownerId
        ));

        mockMvc.perform(post("/api/v1/goals/{goalId}/movements", goalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"WITHDRAW","amount":101.00}
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/goals/{goalId}", goalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
            .andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM savings_goal WHERE id = ?", Integer.class, goalId));

        mockMvc.perform(delete("/api/v1/goals/{goalId}", goalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM savings_goal WHERE id = ?", Integer.class, goalId));
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

    private String goalJson(String name, String amount) {
        return """
            {"name":"%s","targetAmount":%s}
            """.formatted(name, amount);
    }
}
