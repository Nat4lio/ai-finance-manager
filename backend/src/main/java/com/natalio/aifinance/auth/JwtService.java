package com.natalio.aifinance.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration expiration;

    public JwtService(
        JwtEncoder encoder,
        @Value("${app.security.jwt.issuer}") String issuer,
        @Value("${app.security.jwt.expiration}") Duration expiration
    ) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.expiration = expiration;
    }

    public String issueToken(UUID userId) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(expiration))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return expiration.toSeconds();
    }
}