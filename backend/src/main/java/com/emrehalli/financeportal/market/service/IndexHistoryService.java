package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.index.IndexSymbolRegistry;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.emrehalli.financeportal.market.provider.yahoo.YahooHistoricalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches ONE_DAY OHLCV history from Yahoo Finance for BIST index symbols
 * (XU100.IS → BIST100, XU030.IS → BIST30, etc.) and persists each bar to
 * market_price_history with source_name=YAHOO_FINANCE.
 *
 * <p>No derivation step needed — BIST indexes are already denominated in TRY.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexHistoryService {

    private final YahooHistoricalClient yahooHistoricalClient;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceHistoryRepository historyRepository;
    private final IndexSymbolRegistry symbolRegistry;

    public record FetchResult(int fetched, int saved, int skipped) {}

    /**
     * Detects gaps in index ONE_DAY history per symbol and fills them from Yahoo Finance.
     * Finds the last recorded date per symbol, computes the gap to yesterday, and fetches
     * only the missing window. Duplicate-safe — already-existing rows are skipped.
     *
     * <p>If no history exists yet for a symbol, returns empty and logs a warning —
     * run the admin backfill endpoint first.</p>
     */
    @Transactional
    public Map<String, FetchResult> catchUp() {
        Map<String, FetchResult> results = new LinkedHashMap<>();
        for (String code : symbolRegistry.getAllCodes()) {
            String yahooSymbol = symbolRegistry.toYahooSymbol(code);
            results.put(code, catchUpSymbol(yahooSymbol, code));
        }
        return results;
    }

    /**
     * Admin / manual backfill: fetches a fixed look-back window from Yahoo Finance for all index symbols.
     * Duplicate-safe — rows that already exist are skipped.
     *
     * @param days look-back window in calendar days (e.g. 3650 for 10 years)
     */
    @Transactional
    public Map<String, FetchResult> backfillHistory(int days) {
        Map<String, FetchResult> results = new LinkedHashMap<>();
        for (String code : symbolRegistry.getAllCodes()) {
            String yahooSymbol = symbolRegistry.toYahooSymbol(code);
            results.put(code, fetchAndSave(yahooSymbol, code, days));
        }
        return results;
    }

    // ── Private ────────────────────────────────────────────────────────────────

    private FetchResult catchUpSymbol(String yahooSymbol, String code) {
        MarketInstrument instrument = instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.INDEX)
                .orElse(null);

        if (instrument == null) {
            log.warn("IndexHistory catch-up: instrument not in DB. code={}", code);
            return new FetchResult(0, 0, 0);
        }

        Optional<MarketPriceHistory> latest = historyRepository
                .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                        instrument, IntervalType.ONE_DAY, SourceName.YAHOO_FINANCE);

        if (latest.isEmpty()) {
            log.info("IndexHistory catch-up: no history found for code={} — run admin backfill first", code);
            return new FetchResult(0, 0, 0);
        }

        LocalDate lastDate  = latest.get().getPriceTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        if (!lastDate.isBefore(yesterday)) {
            log.debug("IndexHistory catch-up: already up to date. code={} lastDate={}", code, lastDate);
            return new FetchResult(0, 0, 0);
        }

        long gapDays  = ChronoUnit.DAYS.between(lastDate, yesterday);
        int fetchDays = (int) Math.min(gapDays + 3, 60);

        log.info("IndexHistory catch-up: gap detected. code={} lastDate={} yesterday={} gapDays={} fetchDays={}",
                code, lastDate, yesterday, gapDays, fetchDays);

        return fetchAndSave(yahooSymbol, code, fetchDays);
    }

    private FetchResult fetchAndSave(String yahooSymbol, String code, int days) {
        List<StockHistoryDto> bars;
        try {
            bars = yahooHistoricalClient.fetchDailyHistory(yahooSymbol, days);
        } catch (Exception e) {
            log.error("IndexHistory: Yahoo fetch failed. yahooSymbol={} reason={}", yahooSymbol, e.getMessage());
            return new FetchResult(0, 0, 0);
        }

        if (bars.isEmpty()) {
            log.warn("IndexHistory: no bars returned. yahooSymbol={}", yahooSymbol);
            return new FetchResult(0, 0, 0);
        }

        MarketInstrument instrument = resolveInstrument(code);
        int saved = 0, skipped = 0;

        for (StockHistoryDto bar : bars) {
            if (historyRepository.existsByInstrumentAndIntervalTypeAndPriceTimestamp(
                    instrument, IntervalType.ONE_DAY, bar.priceTimestamp())) {
                skipped++;
                continue;
            }
            historyRepository.save(MarketPriceHistory.builder()
                    .instrument(instrument)
                    .openPrice(bar.openPrice())
                    .highPrice(bar.highPrice())
                    .lowPrice(bar.lowPrice())
                    .closePrice(bar.closePrice())
                    .volume(bar.volume())
                    .priceTimestamp(bar.priceTimestamp())
                    .intervalType(IntervalType.ONE_DAY)
                    .sourceName(SourceName.YAHOO_FINANCE)
                    .build());
            saved++;
        }

        log.info("Index history saved: code={} saved={} skipped={} fetched={}", code, saved, skipped, bars.size());
        return new FetchResult(bars.size(), saved, skipped);
    }

    private MarketInstrument resolveInstrument(String code) {
        return instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.INDEX)
                .orElseGet(() -> instrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(code)
                        .instrumentName(symbolRegistry.toDisplayName(code))
                        .instrumentType(InstrumentType.INDEX)
                        .sourceName(SourceName.YAHOO_FINANCE)
                        .build()));
    }
}
