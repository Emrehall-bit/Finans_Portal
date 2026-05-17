package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.commodity.YahooCommodityProvider;
import com.emrehalli.financeportal.market.provider.index.YahooIndexProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.service.CommodityService;
import com.emrehalli.financeportal.market.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for fetching INDEX and COMMODITY data from Yahoo Finance.
 * Runs at the same rate as StockDataFetcher to share Yahoo cookie/crumb lifecycle.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IndexCommodityDataFetcher {

    private final YahooIndexProvider indexProvider;
    private final YahooCommodityProvider commodityProvider;
    private final IndexService indexService;
    private final CommodityService commodityService;

    @Scheduled(fixedRateString = "${market.scheduler.stock-rate-ms:1800000}")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("IndexCommodityDataFetcher.fetch");

        int indexFetched = 0;
        int commodityFetched = 0;
        int failed = 0;

        try {
            indexFetched = fetchIndexes();
        } catch (Exception e) {
            log.error("Index fetch failed, commodity fetch will still proceed", e);
            failed++;
        }

        try {
            commodityFetched = fetchCommodities();
        } catch (Exception e) {
            log.error("Commodity fetch failed", e);
            failed++;
        }

        int total = indexFetched + commodityFetched;
        log.info("IndexCommodityDataFetcher completed. indexCount={}, commodityCount={}, totalCount={}",
                indexFetched, commodityFetched, total);
        run.log(log, total, total, failed);
    }

    private int fetchIndexes() {
        try {
            List<StockPriceDto> quotes = indexProvider.fetch();
            if (quotes.isEmpty()) {
                log.warn("Index provider returned no data");
                return 0;
            }
            indexService.saveAll(quotes);
            log.info("Index data fetched and saved. count={}", quotes.size());
            return quotes.size();
        } catch (DataProviderException e) {
            log.warn("Index provider DataProviderException: {}", e.getMessage());
            return 0;
        }
    }

    private int fetchCommodities() {
        try {
            List<StockPriceDto> quotes = commodityProvider.fetch();
            if (quotes.isEmpty()) {
                log.warn("Commodity provider returned no data");
                return 0;
            }
            commodityService.saveAll(quotes);
            log.info("Commodity data fetched and saved. count={}", quotes.size());
            return quotes.size();
        } catch (DataProviderException e) {
            log.warn("Commodity provider DataProviderException: {}", e.getMessage());
            return 0;
        }
    }
}
