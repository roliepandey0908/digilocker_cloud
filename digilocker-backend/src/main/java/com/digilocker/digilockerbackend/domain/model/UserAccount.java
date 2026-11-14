package com.digilocker.digilockerbackend.domain.model;

import java.time.Instant;

public record UserAccount(
        String id,
        Role role,
        String displayName,
        String email,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt) {
}

