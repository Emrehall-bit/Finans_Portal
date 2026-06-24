package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.domain.enums.BistTier;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for stock market data.
 */
@RestController
@RequestMapping("/api/v1/markets/stocks")
@AllArgsConstructor
@Tag(name = "Hisse Senetleri", description = "BIST hisse senedi piyasa verileri")
public class StockController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final StockService stockService;

    @Operation(summary = "Hisse senetlerini listele")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hisse senetleri başarıyla listelendi"))
    @GetMapping
    public ApiResponse<StockPageResponse> getAll(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) BistTier bistTier) {
        Page<StockPriceDto> result = stockService.getAll(PageRequest.of(page, size), bistTier);
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

    @Operation(summary = "Hisse detayını getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hisse detayı başarıyla döndürüldü"))
    @GetMapping("/{symbol}")
    public ApiResponse<StockPriceDto> getBySymbol(@PathVariable String symbol) {
        StockPriceDto data = stockService.getBySymbol(symbol);
        return ApiResponse.<StockPriceDto>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null && data.dataTimestamp() != null
                        ? LocalDateTime.ofInstant(data.dataTimestamp(), ZoneOffset.UTC)
                        : null)
                .build();
    }

    @Operation(summary = "Hisse fiyat geçmişini getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hisse fiyat geçmişi başarıyla döndürüldü"))
    @GetMapping("/{symbol}/history")
    public ApiResponse<List<StockHistoryDto>> getHistory(@PathVariable String symbol,
                                                         @RequestParam String startDate,
                                                         @RequestParam String endDate) {
        LocalDate parsedStartDate = LocalDate.parse(startDate, DATE_FORMATTER);
        LocalDate parsedEndDate = LocalDate.parse(endDate, DATE_FORMATTER);
        if (parsedEndDate.isBefore(parsedStartDate)) {
            throw new BadRequestException("endDate cannot be before startDate");
        }

        List<StockHistoryDto> data = stockService.getHistory(symbol, parsedStartDate, parsedEndDate);
        return ApiResponse.<List<StockHistoryDto>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data.stream()
                        .map(StockHistoryDto::priceTimestamp)
                        .filter(Objects::nonNull)
                        .map(instant -> LocalDateTime.ofInstant(instant, ZoneOffset.UTC))
                        .max(LocalDateTime::compareTo)
                        .orElse(null))
                .build();
    }

    private LocalDateTime resolveDataDate(List<StockPriceDto> quotes) {
        return quotes.stream()
                .map(StockPriceDto::dataTimestamp)
                .filter(Objects::nonNull)
                .map(instant -> LocalDateTime.ofInstant(instant, ZoneOffset.UTC))
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockPageResponse {
        private List<StockPriceDto> content;
        private long totalElements;
        private int page;
        private int size;
    }
}

