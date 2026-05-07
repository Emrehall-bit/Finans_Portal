package com.emrehalli.financeportal.market.api.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for instrument administration endpoints.
 *
 * @param id instrument identifier
 * @param symbol canonical symbol
 * @param displayName display name
 * @param type instrument type
 * @param enabled enabled flag
 * @param createdAt creation timestamp
 * @param updatedAt update timestamp
 */
public record InstrumentAdminResponse(
        UUID id,
        String symbol,
        String displayName,
        String type,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
