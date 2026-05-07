package com.emrehalli.financeportal.market.api.dto.admin;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request payload for provider mapping create and update operations.
 *
 * @param source provider source
 * @param externalSymbol provider symbol format
 * @param priority priority order
 * @param refreshIntervalMinutes refresh interval in minutes
 * @param historyStartDate history lower bound
 * @param enabled enabled flag
 */
public record InstrumentMappingAdminRequest(
        @NotNull(message = "{validation.instrument.mapping.source.required}")
        DataSource source,
        @NotBlank(message = "{validation.instrument.mapping.externalSymbol.blank}")
        @Size(max = 100, message = "{validation.instrument.mapping.externalSymbol.max}")
        String externalSymbol,
        @Min(value = 1, message = "{validation.instrument.mapping.priority.min}")
        @Max(value = 1000, message = "{validation.instrument.mapping.priority.max}")
        int priority,
        @Min(value = 1, message = "{validation.instrument.mapping.refreshInterval.min}")
        int refreshIntervalMinutes,
        LocalDate historyStartDate,
        @NotNull(message = "{validation.instrument.mapping.enabled.required}")
        Boolean enabled
) {
}
