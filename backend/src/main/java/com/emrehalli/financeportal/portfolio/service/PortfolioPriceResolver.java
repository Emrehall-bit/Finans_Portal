package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class PortfolioPriceResolver {

    private static final Logger logger = LogManager.getLogger(PortfolioPriceResolver.class);

    /**
     * Best-effort price: uses purchase price per unit as a stale fallback when live market quotes are unavailable.
     */
    public PriceResolutionResult resolveCurrentPriceWithFallback(String instrumentCode,
                                                                 BigDecimal purchasePricePerUnit,
                                                                 LocalDateTime referenceTime) {
        if (purchasePricePerUnit != null && purchasePricePerUnit.compareTo(BigDecimal.ZERO) > 0) {
            LocalDateTime ts = referenceTime != null ? referenceTime : LocalDateTime.now();
            logger.debug("Using purchase-price fallback for instrument {}", instrumentCode);
            return PriceResolutionResult.available(purchasePricePerUnit, PriceStatus.STALE, ts);
        }
        logger.debug("No price fallback available for instrument {}", instrumentCode);
        return PriceResolutionResult.unavailable();
    }
}
