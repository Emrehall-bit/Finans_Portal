package com.emrehalli.financeportal.market.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketController {

    private static final Logger logger = LogManager.getLogger(MarketController.class);

    private final MarketQueryService marketQueryService;

    public MarketController(MarketQueryService marketQueryService) {
        this.marketQueryService = marketQueryService;
    }

    @GetMapping
    public ApiResponse<List<MarketDataDto>> getCurrentMarketData(
            @RequestParam(required = false) InstrumentType instrumentType,
            @RequestParam(required = false) String source
    ) {
        logger.info("GET /api/v1/markets called with instrumentType={} source={}", instrumentType, source);

        List<MarketDataDto> data = marketQueryService.getCurrentMarketData();

        if (source != null && !source.isBlank()) {
            data = data.stream()
                    .filter(item -> item.getSource() != null)
                    .filter(item -> item.getSource().equalsIgnoreCase(source))
                    .toList();
        }

        if (instrumentType != null) {
            data = data.stream()
                    .filter(item -> instrumentType == item.getInstrumentType())
                    .toList();
        }

        return ApiResponse.<List<MarketDataDto>>builder()
                .success(true)
                .data(data)
                .message("Current market data fetched successfully")
                .build();
    }

    @GetMapping("/current/{symbol}")
    public ApiResponse<MarketDataDto> getCurrentMarketDataBySymbol(@PathVariable String symbol) {
        logger.info("GET /api/v1/markets/current/{} called", symbol);

        Optional<MarketDataDto> marketData = marketQueryService.findCurrentBySymbol(symbol);

        return ApiResponse.<MarketDataDto>builder()
                .success(true)
                .data(marketData.orElse(null))
                .message(marketData.isPresent()
                        ? "Current market data fetched successfully"
                        : "Market data not found for symbol")
                .build();
    }

    @GetMapping("/history/{symbol}")
    public ApiResponse<List<MarketDataDto>> getHistoricalMarketData(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        logger.info("GET /api/v1/markets/history/{} called", symbol);

        return ApiResponse.<List<MarketDataDto>>builder()
                .success(true)
                .data(marketQueryService.getHistoricalMarketData(symbol, start, end))
                .message("Historical market data fetched successfully")
                .build();
    }

    @GetMapping("/tcmb")
    public ApiResponse<List<MarketDataDto>> getTcmbData() {
        logger.info("GET /api/v1/markets/tcmb called");

        return ApiResponse.<List<MarketDataDto>>builder()
                .success(true)
                .data(marketQueryService.getCurrentMarketDataBySource("EVDS"))
                .message("TCMB market data fetched successfully")
                .build();
    }

    @GetMapping("/futures")
    public ApiResponse<List<MarketDataDto>> getFuturesData(
            @RequestParam(required = false) String source
    ) {
        logger.info("GET /api/v1/markets/futures called with source={}", source);

        List<MarketDataDto> data = marketQueryService.getCurrentMarketData(InstrumentType.FUTURE);

        if (source != null && !source.isBlank()) {
            data = data.stream()
                    .filter(item -> item.getSource() != null)
                    .filter(item -> item.getSource().equalsIgnoreCase(source))
                    .toList();
        }

        return ApiResponse.<List<MarketDataDto>>builder()
                .success(true)
                .data(data)
                .message("Futures market data fetched successfully")
                .build();
    }
}
