package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.provider.index.YahooIndexProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.service.IndexHistoryService;
import com.emrehalli.financeportal.market.service.IndexHistoryService.FetchResult;
import com.emrehalli.financeportal.market.service.IndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin - Endeks Yonetimi", description = "Endeks veri cekme ve gecmis doldurma islemleri")
public class IndexAdminController {

    private final YahooIndexProvider indexProvider;
    private final IndexService indexService;
    private final IndexHistoryService indexHistoryService;

    @Operation(summary = "Endeks verilerini simdi cek", description = "Zamanlayici beklemeden tum endeks kotasyonlarini aninda ceker ve kaydeder")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Endeks verileri basariyla cekildi"))
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

    @Operation(summary = "Endeks gecmis verisi doldur", description = "BIST endeks enstrumanlari icin tarihsel fiyat kayitlarini geriye donuk doldurur")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gecmis veri doldurma islemi tamamlandi"))
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
                    ? "All rows already exist â€” " + totalSkipped + " duplicates skipped."
                    : "No data saved. Check Yahoo Finance session and rate-limit logs.");
        }

        return response;
    }
}

