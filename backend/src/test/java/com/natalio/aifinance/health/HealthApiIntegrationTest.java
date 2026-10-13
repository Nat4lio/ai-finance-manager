package com.natalio.aifinance.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthApiIntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointIsPublicAndReturnsServiceStatusAndIsoTimestamp() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("UP", body.get("status").asText());
        assertEquals("ai-finance-manager-api", body.get("service").asText());
        assertDoesNotThrow(() -> Instant.parse(body.get("timestamp").asText()));
    }
}
