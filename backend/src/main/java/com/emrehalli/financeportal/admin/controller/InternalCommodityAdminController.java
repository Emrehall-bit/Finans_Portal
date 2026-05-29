package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.service.InternalCommodityHistoryService;
import com.emrehalli.financeportal.market.service.InternalCommodityHistoryService.FetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints for internal commodity (GOLD_USD, SILVER_USD) history backfill.
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>POST /api/v1/admin/markets/internal-commodities/history/backfill?days=365
 *       â†’ fetches GC=F + SI=F from Yahoo, saves ONE_DAY INTERNAL history</li>
 *   <li>POST /api/v1/admin/markets/commodities/history/backfill?days=365
 *       â†’ derives GRAM_ALTIN, CEYREK_ALTIN, YARIM_ALTIN, TAM_ALTIN, CUMHURIYET_ALTINI, GUMUS_GRAM history</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/admin/markets/internal-commodities")
@RequiredArgsConstructor
@Slf4j
public class InternalCommodityAdminController {

    private final InternalCommodityHistoryService internalCommodityHistoryService;

    /**
     * Step 1 of history backfill: fetch GC=F (GOLD_USD) and SI=F (SILVER_USD) daily history
     * from Yahoo Finance and persist as INTERNAL source in market_price_history.
     *
     * <p>Example: POST /api/v1/admin/markets/internal-commodities/history/backfill?days=365</p>
     */
    @PostMapping("/history/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> backfillHistory(@RequestParam(defaultValue = "365") int days) {
        log.info("Internal commodity history backfill triggered. days={}", days);

        Map<String, FetchResult> results = internalCommodityHistoryService.backfillHistory(days);

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
                    : "No data saved. Check Yahoo Finance cookie/crumb configuration.");
        } else {
            response.put("nextStep",
                    "POST /api/v1/admin/markets/commodities/history/backfill?days=" + days);
        }

        return response;
    }
}




