package com.emrehalli.financeportal.market.provider.macro.tcmb.dto;

import com.emrehalli.financeportal.market.domain.enums.MacroFrequency;
import com.emrehalli.financeportal.market.domain.enums.MacroUnit;

public record MacroIndicatorDef(
        String code,
        String name,
        MacroFrequency frequency,
        MacroUnit unit
) {}



