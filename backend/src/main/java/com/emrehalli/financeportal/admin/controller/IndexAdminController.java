package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.provider.index.YahooIndexProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.service.IndexHistoryService;
import com.emrehalli.financeportal.market.service.IndexHistoryService.FetchResult;
import com.emrehalli.financeportal.market.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints to manually trigger index data fetches.
 */
@RestController
@RequestMapping("/api/v1/admin/markets/indexes")
@RequiredArgsConstructor
@Slf4j
public class IndexAdminController {

    private final YahooIndexProvider indexProvider;
    private final IndexService indexService;
    private final IndexHistoryService indexHistoryService;

    /**
     * Fetches and persists all index quotes immediately without waiting for the scheduler.
     */
    @PostMapping("/fetch-now")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> fetchNow() {
        log.info("Manual index fetch-now triggered by admin");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("triggeredAt", LocalDateTime.now());

        try {
            List<StockPriceDto> quotes = indexProvider.fetch();
            if (!quotes.isEmpty()) {
                indexService.saveAll(quotes);
            }
            response.put("fetchedCount", quotes.size());
            response.put("symbols", quotes.stream().map(StockPriceDto::symbol).toList());
            log.info("Manual index fetch-now completed. fetchedCount={}", quotes.size());
        } catch (Exception e) {
            log.error("Manual index fetch-now failed", e);
            response.put("fetchedCount", 0);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * Backfills historical ONE_DAY price records for all BIST index instruments.
     * Fetches from Yahoo Finance (.IS symbols) and saves with source_name=YAHOO_FINANCE.
     * Skips rows that already exist.
     *
     * <p>Example: POST /api/v1/admin/markets/indexes/history/backfill?days=3650</p>
     */
    @PostMapping("/history/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> backfillHistory(@RequestParam(defaultValue = "365") int days) {
        log.info("Index history backfill triggered by admin. days={}", days);

        Map<String, FetchResult> results = indexHistoryService.backfillHistory(days);

        int totalFetched = results.values().stream().mapToInt(FetchResult::fetched).sum();
        int totalSaved   = results.values().stream().mapToInt(FetchResult::saved).sum();
        int totalSkipped = results.values().stream().mapToInt(FetchResult::skipped).sum();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("days",         days);
        response.put("totalFetched", totalFetched);
        response.put("totalSaved",   totalSaved);
        response.put("totalSkipped", totalSkipped);
        response.put("bySymbol",     results);
        response.put("triggeredAt",  LocalDateTime.now());

        if (totalSaved == 0) {
            response.put("hint", totalSkipped > 0
                    ? "All rows already exist — " + totalSkipped + " duplicates skipped."
                    : "No data saved. Check Yahoo Finance cookie/crumb configuration.");
        }

        return response;
    }
}
