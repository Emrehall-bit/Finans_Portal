package com.emrehalli.financeportal.market.provider.tefas.dto;

import java.time.LocalDate;

public record TefasFundResponse(
        String symbol,
        String displayName,
        String price,
        String changeRate,
        LocalDate priceDate
) {
}
