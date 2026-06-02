package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.emrehalli.financeportal.market.provider.yahoo.YahooHistoricalClient;
import com.emrehalli.financeportal.market.service.StockService;
import com.emrehalli.financeportal.market.support.BistSymbolRegistry;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisCacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler for fetching and persisting stock history data.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StockHistoryScheduler {

    private static final long SYMBOL_DELAY_MS = 500L;

    private final BistSymbolRegistry bistSymbolRegistry;
    private final StockService stockService;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final YahooHistoricalClient yahooHistoricalClient;
    private final TechnicalAnalysisCacheEvictionService technicalAnalysisCacheEvictionService;

    @Scheduled(cron = "0 30 19 * * MON-FRI")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("StockHistoryScheduler.fetch");
        LocalDate today = LocalDate.now();
        int processedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        boolean historyFetched = false;

        for (String symbol : bistSymbolRegistry.getAllSymbols()) {
            processedCount++;
            try {
                Optional<LocalDate> latestDate = marketPriceHistoryRepository
                        .findTopDateBySymbolAndSourceNameOrderByDateDesc(symbol, SourceName.YAHOO_FINANCE.name());
                if (latestDate.isEmpty()) {
                    log.warn("Skipping stock history scheduler for symbol={} because no existing Yahoo history record was found.", symbol);
                    continue;
                }

                LocalDate startDate = latestDate.get().plusDays(1);
                if (!startDate.isBefore(today)) {
                    continue;
                }

                List<StockHistoryDto> yahooHistory = yahooHistoricalClient.fetchDailyHistory(
                        bistSymbolRegistry.toYahooSymbol(symbol),
                        startDate,
                        today
                );
                stockService.saveYahooHistory(symbol, yahooHistory);
                historyFetched = historyFetched || !yahooHistory.isEmpty();
                successCount++;
            } catch (Exception exception) {
                failedCount++;
                log.warn("Failed to fetch stock history for symbol={}", symbol, exception);
            }

            try {
                Thread.sleep(SYMBOL_DELAY_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Stock history scheduler interrupted during rate-limit delay.", exception);
                run.log(log, processedCount, successCount, failedCount + 1, exception);
                return;
            }
        }
        if (historyFetched) {
            technicalAnalysisCacheEvictionService.evictAllTechnicalCaches();
        }
        run.log(log, processedCount, successCount, failedCount);
    }
}




