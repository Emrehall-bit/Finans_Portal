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

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Scheduler for fetching INDEX and COMMODITY data from Yahoo Finance.
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
        // Step 1: fetch Yahoo quotes and extract GOLD/SILVER inputs
        BigDecimal goldUsdOz = null;
        BigDecimal silverUsdOz = null;
        int fetchedCount = 0;

        try {
            List<StockPriceDto> quotes = commodityProvider.fetch();
            log.info("Yahoo commodity provider returned {} quotes", quotes.size());

            for (StockPriceDto q : quotes) {
                if (q == null || q.symbol() == null || q.price() == null) continue;
                String code = q.symbol().toUpperCase(Locale.ROOT);
                if ("GOLD_USD".equals(code))   goldUsdOz   = q.price();
                if ("SILVER_USD".equals(code))  silverUsdOz = q.price();
            }
            log.info("Yahoo commodity inputs from batch: goldUsdOz={}, silverUsdOz={}",
                    goldUsdOz != null ? goldUsdOz : "not in batch",
                    silverUsdOz != null ? silverUsdOz : "not in batch");

            if (!quotes.isEmpty()) {
                commodityService.saveAll(quotes);
                fetchedCount = quotes.size();
            }
        } catch (DataProviderException e) {
            log.warn("Commodity provider DataProviderException: {}. Will attempt derived calculation with DB values.", e.getMessage());
        }

        // Step 2: calculate derived commodities — explicit params from Yahoo batch,
        // falls back to DB-persisted INTERNAL prices when batch values are null
        try {
            CommodityService.DerivedCommodityResult result =
                    commodityService.calculateAndSaveDerivedCommodities(goldUsdOz, silverUsdOz);
            log.info("Derived calculation result: savedCount={}, symbols={}",
                    result.savedCount(), result.savedSymbols());
        } catch (Exception e) {
            log.error("Derived commodity calculation failed after Yahoo fetch", e);
        }

        return fetchedCount;
    }
}



