package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class PortfolioPriceResolver {

    private static final Logger logger = LogManager.getLogger(PortfolioPriceResolver.class);
    private final MarketQueryService marketQueryService;

    public PortfolioPriceResolver(MarketQueryService marketQueryService) {
        this.marketQueryService = marketQueryService;
    }

    /**
     * Best-effort price: resolves the current market quote first and falls back to the purchase price.
     */
    public PriceResolutionResult resolveCurrentPriceWithFallback(String instrumentCode,
                                                                 BigDecimal purchasePricePerUnit,
                                                                 LocalDateTime referenceTime) {
        if ("TRY".equalsIgnoreCase(instrumentCode)) {
            return PriceResolutionResult.available(BigDecimal.ONE, BigDecimal.ZERO, PriceStatus.LIVE, LocalDateTime.now());
        }

        var snapshot = marketQueryService.findBySymbol(instrumentCode).orElse(null);
        if (snapshot != null && snapshot.price() != null) {
            logger.debug("Using market price for instrument {}", instrumentCode);
            return PriceResolutionResult.available(
                    snapshot.price(),
                    snapshot.changeRate(),
                    PriceStatus.LIVE,
                    snapshot.fetchedAt()
            );
        }

        if (purchasePricePerUnit != null && purchasePricePerUnit.compareTo(BigDecimal.ZERO) > 0) {
            LocalDateTime ts = referenceTime != null ? referenceTime : LocalDateTime.now();
            logger.debug("Using purchase-price fallback for instrument {}", instrumentCode);
            return PriceResolutionResult.available(purchasePricePerUnit, null, PriceStatus.STALE, ts);
        }
        logger.debug("No price fallback available for instrument {}", instrumentCode);
        return PriceResolutionResult.unavailable();
    }
}
