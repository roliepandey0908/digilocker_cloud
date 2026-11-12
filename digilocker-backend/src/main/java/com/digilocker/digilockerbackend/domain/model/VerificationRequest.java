package com.digilocker.digilockerbackend.domain.model;

import java.time.Instant;
import java.util.List;

public record VerificationRequest(
        String id,
        String requesterUserId,
        String citizenId,
        String purpose,
        List<String> documentIds,
        VerificationRequestStatus status,
        Instant expiresAt,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt) {
    public VerificationRequest {
        documentIds = List.copyOf(documentIds);
    }
}

