package com.emrehalli.financeportal.market.provider.macro.tcmb.dto;

import java.util.List;

public record MacroSeriesRequest(
        String evdsSeries,
        List<MacroSeriesField> fields,
        List<MacroIndicatorDef> indicators,
        String aggregationType,
        String formula,
        String startDate,
        String endDate,
        String dateFormat
) {}




