package com.emrehalli.financeportal.market.api.dto.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for provider mapping administration endpoints.
 *
 * @param id mapping identifier
 * @param instrumentId parent instrument identifier
 * @param source provider source
 * @param externalSymbol provider symbol
 * @param priority priority order
 * @param refreshIntervalMinutes refresh interval in minutes
 * @param historyStartDate history lower bound
 * @param enabled enabled flag
 * @param lastRefreshedAt last success timestamp
 * @param lastRefreshStatus last refresh status
 * @param createdAt creation timestamp
 * @param updatedAt update timestamp
 */
public record InstrumentMappingAdminResponse(
        UUID id,
        UUID instrumentId,
        String source,
        String externalSymbol,
        int priority,
        int refreshIntervalMinutes,
        LocalDate historyStartDate,
        boolean enabled,
        Instant lastRefreshedAt,
        String lastRefreshStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
