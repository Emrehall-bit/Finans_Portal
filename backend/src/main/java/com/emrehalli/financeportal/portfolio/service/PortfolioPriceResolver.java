package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.domain.enums.MarketPriceStatus;
import com.emrehalli.financeportal.market.service.MarketPriceReader;
import com.emrehalli.financeportal.market.service.model.CurrentPriceSnapshot;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PortfolioPriceResolver {

    private static final Logger logger = LogManager.getLogger(PortfolioPriceResolver.class);

    private final MarketPriceReader marketQueryService;

    public PortfolioPriceResolver(MarketPriceReader marketQueryService) {
        this.marketQueryService = marketQueryService;
    }

    /**
     * Best-effort price: resolves the current market quote first and falls back to the purchase price.
     */
    public PriceResolutionResult resolveCurrentPriceWithFallback(String instrumentCode,
                                                                 BigDecimal purchasePricePerUnit,
                                                                 LocalDateTime referenceTime) {
        PriceResolutionResult marketPrice = resolveMarketPrice(instrumentCode);
        if (marketPrice.valuationAvailable()) {
            return marketPrice;
        }

        if (purchasePricePerUnit != null && purchasePricePerUnit.compareTo(BigDecimal.ZERO) > 0) {
            LocalDateTime ts = referenceTime != null ? referenceTime : LocalDateTime.now();
            logger.debug("Using purchase-price fallback for instrument {}", instrumentCode);
            return PriceResolutionResult.available(purchasePricePerUnit, PriceStatus.STALE, ts);
        }
        logger.debug("No price fallback available for instrument {}", instrumentCode);
        return PriceResolutionResult.unavailable();
    }

    private PriceResolutionResult resolveMarketPrice(String instrumentCode) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            return PriceResolutionResult.unavailable();
        }

        try {
            CurrentPriceSnapshot snapshot = marketQueryService.resolveCurrentPrice(instrumentCode);
            if (!snapshot.priceAvailable() || snapshot.price() == null || snapshot.price().compareTo(BigDecimal.ZERO) <= 0) {
                return PriceResolutionResult.unavailable();
            }

            return PriceResolutionResult.available(
                    snapshot.price(),
                    snapshot.priceStatus() == MarketPriceStatus.LIVE ? PriceStatus.LIVE : PriceStatus.STALE,
                    resolveSnapshotTime(snapshot)
            );
        } catch (RuntimeException ex) {
            logger.warn("Market price resolution failed for instrument {}: {}", instrumentCode, ex.getMessage());
            return PriceResolutionResult.unavailable();
        }
    }

    private LocalDateTime resolveSnapshotTime(CurrentPriceSnapshot snapshot) {
        Instant quoteTime = snapshot.lastUpdatedAt() != null
                ? snapshot.lastUpdatedAt()
                : snapshot.priceTime() != null ? snapshot.priceTime() : snapshot.fetchedAt();
        return quoteTime == null
                ? null
                : LocalDateTime.ofInstant(quoteTime, ZoneOffset.UTC);
    }
}
