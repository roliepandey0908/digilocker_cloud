package com.digilocker.digilockerbackend.domain.model;

import java.time.Instant;

public record IssuerMembership(
        String id,
        String issuerOrganizationId,
        String userId,
        Instant createdAt) {
}

