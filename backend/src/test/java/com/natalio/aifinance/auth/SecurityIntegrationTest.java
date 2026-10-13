package com.natalio.aifinance.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.natalio.aifinance.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void protectedRoutesRejectMissingInvalidAndTamperedTokens() throws Exception {
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized());

        String token = registerAndGetToken();
        String tamperedToken = token.substring(0, token.length() - 1)
            + (token.endsWith("a") ? "b" : "a");

        mockMvc.perform(get("/actuator/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    private String registerAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"security@example.com","password":"A-long-password-123","displayName":"Security"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
    }
}
