package com.emrehalli.financeportal.market.api.dto.admin;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for instrument create and update operations.
 *
 * @param symbol canonical symbol input
 * @param displayName display name shown to clients
 * @param type instrument type
 * @param enabled enabled flag
 */
public record InstrumentAdminRequest(
        @NotBlank(message = "{validation.instrument.symbol.blank}")
        @Size(max = 50, message = "{validation.instrument.symbol.max}")
        String symbol,
        @NotBlank(message = "{validation.instrument.displayName.blank}")
        @Size(max = 255, message = "{validation.instrument.displayName.max}")
        String displayName,
        @NotNull(message = "{validation.instrument.type.required}")
        InstrumentType type,
        @NotNull(message = "{validation.instrument.enabled.required}")
        Boolean enabled
) {
}
