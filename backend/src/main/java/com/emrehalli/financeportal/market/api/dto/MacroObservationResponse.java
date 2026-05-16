package com.emrehalli.financeportal.market.api.dto;

import com.emrehalli.financeportal.market.domain.enums.MacroSourceName;
import com.emrehalli.financeportal.market.domain.enums.MacroValueType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MacroObservationResponse(
        String periodLabel,
        LocalDate observationDate,
        BigDecimal value,
        MacroValueType valueType,
        MacroSourceName source
) {}
