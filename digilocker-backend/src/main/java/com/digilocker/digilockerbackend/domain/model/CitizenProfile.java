package com.digilocker.digilockerbackend.domain.model;

import java.time.Instant;

public record CitizenProfile(
        String id,
        String userId,
        String enrollmentId,
        String issuerOrganizationId,
        CitizenProfileStatus status,
        Instant createdAt,
        Instant updatedAt) {
}


