package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.provider.stock.StockProviderChain;
import com.emrehalli.financeportal.market.provider.stock.dto.StockQuoteDto;
import com.emrehalli.financeportal.market.service.StockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduler for fetching stock market data.
 */
@Component
@Slf4j
@AllArgsConstructor
public class StockDataFetcher {

    private static final LocalTime MARKET_OPEN = LocalTime.of(10, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(18, 0);

    private final StockProviderChain stockProviderChain;
    private final StockService stockService;

    @Scheduled(fixedRateString = "${market.scheduler.stock-rate-ms:1800000}")
    public void fetch() {
        LocalDateTime now = LocalDateTime.now();
        if (!isMarketHours(now)) {
            return;
        }

        try {
            List<?> result = stockProviderChain.fetch();
            if (result.isEmpty()) {
                log.warn("Stock provider chain returned no stock data during market hours.");
                return;
            }
            stockService.saveAll(result.stream()
                    .filter(StockQuoteDto.class::isInstance)
                    .map(StockQuoteDto.class::cast)
                    .toList());
        } catch (Exception exception) {
            log.error("Failed to fetch stock data.", exception);
        }
    }

    private boolean isMarketHours(LocalDateTime dateTime) {
        DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime time = dateTime.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }
}
