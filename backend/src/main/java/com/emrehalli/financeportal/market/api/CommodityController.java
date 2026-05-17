package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.service.CommodityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for commodity market data (gold, silver, oil, gas).
 */
@RestController
@RequestMapping("/api/v1/markets/commodities")
@RequiredArgsConstructor
public class CommodityController {

    private final CommodityService commodityService;

    @GetMapping
    public ApiResponse<List<MarketQuoteResponse>> getAll() {
        List<MarketQuoteResponse> data = commodityService.getAll();
        return ApiResponse.<List<MarketQuoteResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(data))
                .build();
    }

    private LocalDateTime resolveDataDate(List<MarketQuoteResponse> items) {
        return items.stream()
                .map(MarketQuoteResponse::updatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}
