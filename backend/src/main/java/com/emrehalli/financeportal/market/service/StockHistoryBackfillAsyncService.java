package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.emrehalli.financeportal.market.provider.yahoo.YahooHistoricalClient;
import com.emrehalli.financeportal.market.support.BistSymbolRegistry;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisCacheEvictionService;
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

    private final YahooHistoricalClient yahooHistoricalClient;
    private final BistSymbolRegistry bistSymbolRegistry;
    private final StockService stockService;
    private final BackfillStatus backfillStatus;
    private final TechnicalAnalysisCacheEvictionService technicalAnalysisCacheEvictionService;

    @Async
    public void runBackfillAsync(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        backfillStatus.start(symbols.size());
        boolean historyFetched = false;
        try {
            for (int index = 0; index < symbols.size(); index++) {
                String symbol = symbols.get(index);
                try {
                    List<StockHistoryDto> yahooHistory = yahooHistoricalClient.fetchDailyHistory(
                            bistSymbolRegistry.toYahooSymbol(symbol),
                            startDate,
                            endDate
                    );
                    stockService.saveYahooHistory(symbol, yahooHistory);
                    historyFetched = historyFetched || !yahooHistory.isEmpty();
                    log.info("{}: {} Yahoo OHLCV kaydi islendi", symbol, yahooHistory.size());
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
            if (historyFetched) {
                technicalAnalysisCacheEvictionService.evictAllTechnicalCaches();
            }
            backfillStatus.finish();
        }
    }
}




