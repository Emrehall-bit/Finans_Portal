package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.provider.index.YahooIndexProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoint to manually trigger an index data fetch.
 */
@RestController
@RequestMapping("/api/v1/admin/markets/indexes")
@RequiredArgsConstructor
@Slf4j
public class IndexAdminController {

    private final YahooIndexProvider indexProvider;
    private final IndexService indexService;

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
}
