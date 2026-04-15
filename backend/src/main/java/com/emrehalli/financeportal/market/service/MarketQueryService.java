package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketDataCacheService;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketQueryService {

    private static final Logger logger = LogManager.getLogger(MarketQueryService.class);

    private final MarketDataCacheService marketDataCacheService;

    public MarketQueryService(MarketDataCacheService marketDataCacheService) {
        this.marketDataCacheService = marketDataCacheService;
    }

    public List<MarketDataDto> getTcmbMarketData() {
        logger.info("Fetching TCMB data from cache in MarketQueryService...");

        // Veriyi hemen dönmek yerine bir değişkene alıyoruz
        List<MarketDataDto> data = marketDataCacheService.getTcmbData();

        // Kaç adet veri geldiğini logluyoruz (Null güvenliği ile birlikte)
        logger.info("TCMB data fetched from cache. Count: {}", data == null ? 0 : data.size());

        return data;
    }
}