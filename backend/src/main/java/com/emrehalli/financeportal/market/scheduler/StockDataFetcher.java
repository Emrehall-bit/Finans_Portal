package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.provider.stock.StockProviderChain;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.service.StockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for fetching stock market data.
 */
@Component
@Slf4j
@AllArgsConstructor
public class StockDataFetcher {

    private final StockProviderChain stockProviderChain;
    private final StockService stockService;

    @Scheduled(fixedRateString = "${market.scheduler.stock-rate-ms:1800000}")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("StockDataFetcher.fetch");
        int processedCount = 0;
        int successCount = 0;
        try {
            List<?> result = stockProviderChain.fetch();
            processedCount = result.size();
            if (result.isEmpty()) {
                log.warn("Stock provider chain returned no stock data during market hours.");
                run.log(log, 0, 0, 0);
                return;
            }
            List<StockPriceDto> prices = result.stream()
                    .filter(StockPriceDto.class::isInstance)
                    .map(StockPriceDto.class::cast)
                    .toList();
            stockService.saveAll(prices);
            successCount = prices.size();
            run.log(log, processedCount, successCount, processedCount - successCount);
        } catch (Exception exception) {
            log.error("Failed to fetch stock data.", exception);
            run.log(log, processedCount, successCount, processedCount == 0 ? 1 : processedCount - successCount, exception);
        }
    }
}




