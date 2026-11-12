package com.digilocker.digilockerbackend.domain.model;

import java.time.Instant;

public record IssuerOrganization(
        String id,
        String name,
        OrganizationStatus status,
        Instant createdAt,
        Instant updatedAt) {
}


