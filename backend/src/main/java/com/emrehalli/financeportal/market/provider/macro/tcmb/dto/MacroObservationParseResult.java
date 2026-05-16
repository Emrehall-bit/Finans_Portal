package com.emrehalli.financeportal.market.provider.macro.tcmb.dto;

import com.emrehalli.financeportal.market.domain.enums.MacroValueType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MacroObservationParseResult(
        String indicatorCode,
        String periodLabel,
        LocalDate observationDate,
        BigDecimal value,
        MacroValueType valueType
) {}
