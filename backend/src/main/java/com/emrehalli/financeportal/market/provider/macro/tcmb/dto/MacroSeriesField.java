package com.emrehalli.financeportal.market.provider.macro.tcmb.dto;

import com.emrehalli.financeportal.market.domain.enums.MacroValueType;

public record MacroSeriesField(String fieldName, MacroValueType valueType, String indicatorCode) {}



