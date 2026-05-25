package com.emrehalli.financeportal.common.logging;

import java.time.Instant;

public record AuditEvent(
        Instant timestamp,
        String userId,
        String username,
        String role,
        AuditAction action,
        String resourceType,
        String resourceId,
        String endpoint,
        String method,
        String ipAddress,
        String userAgent,
        boolean success,
        long durationMs
) {
}



