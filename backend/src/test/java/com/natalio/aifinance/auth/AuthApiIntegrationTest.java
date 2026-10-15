package com.natalio.aifinance.auth;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.natalio.aifinance.user.AppUserRepository;

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
    void registrationPersistsUsernameAndAllowsCaseInsensitiveLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"Person_01","password":"A-long-password-123"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken", not(emptyString())))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900));

        org.junit.jupiter.api.Assertions.assertEquals(1, users.count());
        org.junit.jupiter.api.Assertions.assertEquals("person_01",
            users.findByUsernameIgnoreCase("PERSON_01").orElseThrow().getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("person_01",
            users.findByUsernameIgnoreCase("person_01").orElseThrow().getDisplayName());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"PERSON_01","password":"A-long-password-123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(emptyString())))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void registrationRejectsUsernameDuplicatesRegardlessOfCase() throws Exception {
        register("person_01");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"PERSON_01","password":"A-long-password-123"}
                    """))
            .andExpect(status().isConflict());

        org.junit.jupiter.api.Assertions.assertEquals(1, users.count());
    }

    @Test
    void registrationRejectsInvalidDataAndPasswordsOverBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"invalid name!","password":"short"}
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"ab","password":"A-long-password-123"}
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
                    {"username":"person_01","password":"éééééééééééééééééééééééééééééééééééééé"}
                    """))
            .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(0, users.count());
    }

    @Test
    void loginRejectsWrongAndUnknownCredentialsWithoutRevealingWhichFailed() throws Exception {
        register("person_01");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"person_01","password":"incorrect-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"unknown_user","password":"incorrect-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"A-long-password-123"}
                    """.formatted(username)))
            .andExpect(status().isCreated());
    }
}
