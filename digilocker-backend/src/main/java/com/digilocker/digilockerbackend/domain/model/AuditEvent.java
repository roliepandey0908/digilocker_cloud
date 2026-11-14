package com.digilocker.digilockerbackend.domain.model;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        String id,
        String actorUserId,
        String action,
        String resourceType,
        String resourceId,
        Instant occurredAt,
        Map<String, String> details) {
    public AuditEvent {
        details = Map.copyOf(details);
    }
}

