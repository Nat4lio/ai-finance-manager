package com.natalio.aifinance.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 12, max = 72) String password,
    @NotBlank @Size(max = 120) String displayName
) {
}