package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketDataCacheService;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MarketQueryService {

    private static final Logger logger = LogManager.getLogger(MarketQueryService.class);

    private final MarketDataCacheService marketDataCacheService;
    private final MarketPersistenceService marketPersistenceService;

    public MarketQueryService(MarketDataCacheService marketDataCacheService,
                              MarketPersistenceService marketPersistenceService) {
        this.marketDataCacheService = marketDataCacheService;
        this.marketPersistenceService = marketPersistenceService;
    }

    public List<MarketDataDto> getCurrentMarketData() {
        logger.info("Fetching current market data from cache");
        return marketDataCacheService.getCurrentData();
    }

    public List<MarketDataDto> getCurrentMarketData(InstrumentType instrumentType) {
        logger.info("Fetching current market data by instrument type: {}", instrumentType);
        return marketDataCacheService.getCurrentDataByType(instrumentType);
    }

    public List<MarketDataDto> getCurrentMarketDataBySource(String source) {
        logger.info("Fetching current market data by source: {}", source);
        return marketDataCacheService.getCurrentDataByProvider(source);
    }

    public Optional<MarketDataDto> findCurrentBySymbol(String symbol) {
        logger.info("Fetching current market data by symbol: {}", symbol);
        return marketDataCacheService.findCurrentBySymbol(symbol);
    }

    public Optional<MarketDataDto> findLastPersistedBySymbol(String symbol) {
        logger.info("Fetching last persisted market data by symbol: {}", symbol);
        return marketPersistenceService.findLatestBySymbol(symbol);
    }

    public List<MarketDataDto> getHistoricalMarketData(String symbol, LocalDateTime start, LocalDateTime end) {
        logger.info("Fetching historical market data for symbol: {} between {} and {}", symbol, start, end);
        return marketPersistenceService.getHistoricalData(symbol, start, end);
    }

    @Deprecated(forRemoval = false)
    public List<MarketDataDto> getTcmbMarketData() {
        return getCurrentMarketDataBySource("EVDS");
    }
}
