package com.natalio.aifinance.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.natalio.aifinance.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void registrationPersistsUserAndAllowsCaseInsensitiveLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"Person@example.com","password":"A-long-password-123","displayName":"Person"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken", not(emptyString())))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900));

        org.junit.jupiter.api.Assertions.assertEquals(1, users.count());
        org.junit.jupiter.api.Assertions.assertEquals("person@example.com",
            users.findByEmailIgnoreCase("PERSON@example.com").orElseThrow().getEmail());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"PERSON@EXAMPLE.COM","password":"A-long-password-123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(emptyString())))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void registrationRejectsEmailDuplicatesRegardlessOfCase() throws Exception {
        register("person@example.com");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"PERSON@EXAMPLE.COM","password":"A-long-password-123","displayName":"Person"}
                    """))
            .andExpect(status().isConflict());

        org.junit.jupiter.api.Assertions.assertEquals(1, users.count());
    }

    @Test
    void registrationRejectsInvalidDataAndPasswordsOverBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"not-an-email","password":"short","displayName":" "}
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Malformed request body"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"person@example.com","password":"éééééééééééééééééééééééééééééééééééééé","displayName":"Person"}
                    """))
            .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(0, users.count());
    }

    @Test
    void loginRejectsWrongAndUnknownCredentialsWithoutRevealingWhichFailed() throws Exception {
        register("person@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"person@example.com","password":"incorrect-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"unknown@example.com","password":"incorrect-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"A-long-password-123","displayName":"Person"}
                    """.formatted(email)))
            .andExpect(status().isCreated());
    }
}
