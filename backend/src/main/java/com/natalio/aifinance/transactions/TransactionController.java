package com.natalio.aifinance.transactions;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionCreatedResponse> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateTransactionRequest request
    ) {
        TransactionCreatedResponse created = transactionService.create(
            UUID.fromString(jwt.getSubject()),
            request
        );
        return ResponseEntity.status(201).body(created);
    }

    @DeleteMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID transactionId
    ) {
        transactionService.delete(UUID.fromString(jwt.getSubject()), transactionId);
    }
}
