package com.emrehalli.financeportal.common.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class AuditEventLogger {

    private static final Logger auditLogger = LogManager.getLogger("AUDIT");

    public void log(AuditEvent event) {
        try (StructuredLogContext ignored = StructuredLogContext.open()
                .put(LoggingConstants.LOG_TYPE_KEY, "audit")
                .put(LoggingConstants.USER_ID_KEY, event.userId())
                .put(LoggingConstants.USERNAME_KEY, event.username())
                .put(LoggingConstants.ROLE_KEY, event.role())
                .put(LoggingConstants.ACTION_KEY, event.action())
                .put(LoggingConstants.RESOURCE_TYPE_KEY, event.resourceType())
                .put(LoggingConstants.RESOURCE_ID_KEY, event.resourceId())
                .put(LoggingConstants.ENDPOINT_KEY, event.endpoint())
                .put(LoggingConstants.METHOD_KEY, event.method())
                .put(LoggingConstants.IP_ADDRESS_KEY, event.ipAddress())
                .put(LoggingConstants.USER_AGENT_KEY, event.userAgent())
                .put(LoggingConstants.SUCCESS_KEY, event.success())
                .put(LoggingConstants.DURATION_MS_KEY, event.durationMs())) {
            auditLogger.info("Audit event recorded");
        }
    }
}



