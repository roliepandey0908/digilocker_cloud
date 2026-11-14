package com.digilocker.digilockerbackend.domain.model;

import java.time.Instant;

public record DocumentMetadata(
        String id,
        String documentUri,
        String citizenId,
        String issuerOrganizationId,
        String issuedByUserId,
        String documentType,
        String title,
        String fileName,
        String mediaType,
        long sizeBytes,
        String sha256,
        String storageRef,
        DocumentStatus status,
        Instant issuedAt,
        Instant createdAt,
        Instant updatedAt) {
}

