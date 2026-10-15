package com.natalio.aifinance.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.natalio.aifinance.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AppUserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void registerRejectsExistingUsernameWithoutEncodingPassword() {
        when(users.existsByUsernameIgnoreCase("person")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> authService.register(new RegisterRequest(" Person ", "A-long-password-123")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(passwordEncoder, never()).encode("A-long-password-123");
        verify(users, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginUsesGenericUnauthorizedResponseForInvalidCredentials() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new BadCredentialsException("invalid credentials"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> authService.login(new LoginRequest(" Person ", "incorrect-password")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid username or password", exception.getReason());
        verify(users, never()).findByUsernameIgnoreCase("person");
    }

    @Test
    void registerRejectsPasswordsThatExceedBcryptByteLimit() {
        String multibytePassword = "é".repeat(37);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("person", multibytePassword)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(users, never()).existsByUsernameIgnoreCase("person");
    }
}