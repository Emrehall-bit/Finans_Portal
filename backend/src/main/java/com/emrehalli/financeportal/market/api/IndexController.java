package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.service.IndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for global stock index data.
 */
@RestController
@RequestMapping("/api/v1/markets/indexes")
@RequiredArgsConstructor
public class IndexController {

    private final IndexService indexService;

    @GetMapping
    public ApiResponse<List<MarketQuoteResponse>> getAll() {
        List<MarketQuoteResponse> data = indexService.getAll();
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




