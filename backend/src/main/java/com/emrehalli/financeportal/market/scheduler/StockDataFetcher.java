package com.emrehalli.financeportal.market.scheduler;

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
        System.out.println(">>> FETCH CALLED <<<");
        log.error(">>> FETCH CALLED <<<");
        try {
            List<?> result = stockProviderChain.fetch();
            if (result.isEmpty()) {
                log.warn("Stock provider chain returned no stock data during market hours.");
                return;
            }
            stockService.saveAll(result.stream()
                    .filter(StockPriceDto.class::isInstance)
                    .map(StockPriceDto.class::cast)
                    .toList());
        } catch (Exception exception) {
            log.error("Failed to fetch stock data.", exception);
        }
    }
}
