package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.provider.stock.IsYatirimStockHistoryProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockHistoryBackfillAsyncService {

    private static final long SYMBOL_DELAY_MS = 1000L;

    private final IsYatirimStockHistoryProvider isYatirimStockHistoryProvider;
    private final StockService stockService;
    private final BackfillStatus backfillStatus;

    @Async
    public void runBackfillAsync(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        backfillStatus.start(symbols.size());
        try {
            for (int index = 0; index < symbols.size(); index++) {
                String symbol = symbols.get(index);
                try {
                    List<StockHistoryDto> history = isYatirimStockHistoryProvider.fetchHistory(symbol, startDate, endDate);
                    stockService.saveHistory(symbol, history);
                    log.info("{}: {} kayit islendi", symbol, history.size());
                } catch (Exception exception) {
                    log.error("Stock history backfill failed for symbol={}", symbol, exception);
                } finally {
                    backfillStatus.incrementProcessed();
                }

                if (symbols.size() > 1 && index < symbols.size() - 1) {
                    try {
                        Thread.sleep(SYMBOL_DELAY_MS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        log.error("Stock history backfill interrupted during rate-limit delay.", exception);
                        break;
                    }
                }
            }
        } finally {
            backfillStatus.finish();
        }
    }
}



