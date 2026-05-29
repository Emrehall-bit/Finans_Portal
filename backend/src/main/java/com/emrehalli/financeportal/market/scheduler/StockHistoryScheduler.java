package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.stock.IsYatirimStockHistoryProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.emrehalli.financeportal.market.service.StockService;
import com.emrehalli.financeportal.market.support.BistSymbolRegistry;
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
    private final IsYatirimStockHistoryProvider isYatirimStockHistoryProvider;

    @Scheduled(cron = "0 30 19 * * MON-FRI")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("StockHistoryScheduler.fetch");
        LocalDate today = LocalDate.now();
        int processedCount = 0;
        int successCount = 0;
        int failedCount = 0;

        for (String symbol : bistSymbolRegistry.getAllSymbols()) {
            processedCount++;
            try {
                Optional<LocalDate> latestDate = marketPriceHistoryRepository.findTopDateBySymbolOrderByDateDesc(symbol);
                if (latestDate.isEmpty()) {
                    log.warn("Skipping stock history scheduler for symbol={} because no existing history record was found.", symbol);
                    continue;
                }

                LocalDate startDate = latestDate.get().plusDays(1);
                if (!startDate.isBefore(today)) {
                    continue;
                }

                List<StockHistoryDto> history = isYatirimStockHistoryProvider.fetchHistory(symbol, startDate, today);
                stockService.saveHistory(symbol, history);
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
        run.log(log, processedCount, successCount, failedCount);
    }
}




