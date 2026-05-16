package com.emrehalli.financeportal.market.api.dto;

import com.emrehalli.financeportal.market.domain.enums.MacroFrequency;
import com.emrehalli.financeportal.market.domain.enums.MacroSourceName;
import com.emrehalli.financeportal.market.domain.enums.MacroUnit;

public record MacroIndicatorResponse(
        String code,
        String name,
        MacroSourceName source,
        MacroFrequency frequency,
        MacroUnit unit,
        boolean active
) {}
