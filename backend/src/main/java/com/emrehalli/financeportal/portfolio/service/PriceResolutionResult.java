package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.portfolio.enums.PriceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Safe wrapper for best-effort price resolution without throwing.
public record PriceResolutionResult(
        BigDecimal price,
        PriceStatus priceStatus,
        LocalDateTime lastPriceUpdateTime,
        boolean valuationAvailable
) {

    public static PriceResolutionResult available(BigDecimal price,
                                                  PriceStatus priceStatus,
                                                  LocalDateTime lastPriceUpdateTime) {
        return new PriceResolutionResult(price, priceStatus, lastPriceUpdateTime, true);
    }

    public static PriceResolutionResult unavailable() {
        return new PriceResolutionResult(null, PriceStatus.UNAVAILABLE, null, false);
    }
}



