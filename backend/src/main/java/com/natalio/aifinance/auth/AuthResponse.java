package com.natalio.aifinance.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresIn) {
}