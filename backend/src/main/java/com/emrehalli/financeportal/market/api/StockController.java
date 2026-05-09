package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.provider.stock.dto.StockQuoteDto;
import com.emrehalli.financeportal.market.service.StockService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for stock market data.
 */
@RestController
@RequestMapping("/api/v1/markets/stocks")
@AllArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping
    public ApiResponse<StockPageResponse> getAll(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Page<StockQuoteDto> result = stockService.getAll(PageRequest.of(page, size));
        StockPageResponse data = StockPageResponse.builder()
                .content(result.getContent())
                .totalElements(result.getTotalElements())
                .page(result.getNumber())
                .size(result.getSize())
                .build();

        return ApiResponse.<StockPageResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(result.getContent()))
                .build();
    }

    @GetMapping("/{symbol}")
    public ApiResponse<StockQuoteDto> getBySymbol(@PathVariable String symbol) {
        StockQuoteDto data = stockService.getBySymbol(symbol);
        return ApiResponse.<StockQuoteDto>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null ? data.getDataTimestamp() : null)
                .build();
    }

    private LocalDateTime resolveDataDate(List<StockQuoteDto> quotes) {
        return quotes.stream()
                .map(StockQuoteDto::getDataTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * Stock page response payload.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockPageResponse {
        private List<StockQuoteDto> content;
        private long totalElements;
        private int page;
        private int size;
    }
}
