package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class PortfolioPriceResolver {

    private static final Logger logger = LogManager.getLogger(PortfolioPriceResolver.class);

    private final MarketQueryService marketQueryService;

    public PortfolioPriceResolver(MarketQueryService marketQueryService) {
        this.marketQueryService = marketQueryService;
    }

    // Best-effort resolution order: current cache-backed quote, last persisted quote, unavailable.
    public PriceResolutionResult resolveCurrentPriceWithFallback(String instrumentCode) {
        Optional<PriceResolutionResult> liveOrCached = safeResolveCurrentQuote(instrumentCode);
        if (liveOrCached.isPresent()) {
            return liveOrCached.get();
        }

        Optional<PriceResolutionResult> stale = safeResolveLastPersistedQuote(instrumentCode);
        if (stale.isPresent()) {
            return stale.get();
        }

        return PriceResolutionResult.unavailable();
    }

    private Optional<PriceResolutionResult> safeResolveCurrentQuote(String instrumentCode) {
        try {
            return marketQueryService.findCurrentBySymbol(instrumentCode)
                    .filter(dto -> dto.getPrice() != null)
                    .map(dto -> PriceResolutionResult.available(
                            dto.getPrice(),
                            mapCurrentPriceStatus(dto),
                            resolveTimestamp(dto)
                    ));
        } catch (Exception ex) {
            logger.warn("Current market quote lookup failed for instrument {}", instrumentCode, ex);
            return Optional.empty();
        }
    }

    private Optional<PriceResolutionResult> safeResolveLastPersistedQuote(String instrumentCode) {
        try {
            return marketQueryService.findLastPersistedBySymbol(instrumentCode)
                    .filter(dto -> dto.getPrice() != null)
                    .map(dto -> PriceResolutionResult.available(
                            dto.getPrice(),
                            PriceStatus.STALE,
                            resolveTimestamp(dto)
                    ));
        } catch (Exception ex) {
            logger.warn("Persisted market quote lookup failed for instrument {}", instrumentCode, ex);
            return Optional.empty();
        }
    }

    private PriceStatus mapCurrentPriceStatus(MarketDataDto dto) {
        MarketDataFreshness freshness = dto.getFreshness();
        if (freshness == MarketDataFreshness.REALTIME) {
            return PriceStatus.LIVE;
        }
        return PriceStatus.CACHED;
    }

    private LocalDateTime resolveTimestamp(MarketDataDto dto) {
        return dto.getPriceTime() != null ? dto.getPriceTime() : dto.getFetchedAt();
    }
}
