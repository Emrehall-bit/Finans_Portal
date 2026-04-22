package com.emrehalli.financeportal.market.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.dto.event.MarketEventDto;
import com.emrehalli.financeportal.market.service.MarketEventQueryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market-events")
public class MarketEventController {

    private static final Logger logger = LogManager.getLogger(MarketEventController.class);

    private final MarketEventQueryService marketEventQueryService;

    public MarketEventController(MarketEventQueryService marketEventQueryService) {
        this.marketEventQueryService = marketEventQueryService;
    }

    @GetMapping
    public ApiResponse<List<MarketEventDto>> getMarketEvents(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String eventType
    ) {
        logger.info("GET /api/v1/market-events called with source={} symbol={} eventType={}", source, symbol, eventType);

        List<MarketEventDto> events = marketEventQueryService.getLatestEvents();

        if (source != null && !source.isBlank()) {
            events = events.stream()
                    .filter(item -> item.getSource() != null)
                    .filter(item -> item.getSource().equalsIgnoreCase(source))
                    .toList();
        }

        if (symbol != null && !symbol.isBlank()) {
            String normalizedSymbol = symbol.trim();
            events = events.stream()
                    .filter(item -> item.getSymbol() != null)
                    .filter(item -> item.getSymbol().equalsIgnoreCase(normalizedSymbol))
                    .toList();
        }

        if (eventType != null && !eventType.isBlank()) {
            events = events.stream()
                    .filter(item -> item.getEventType() != null)
                    .filter(item -> item.getEventType().equalsIgnoreCase(eventType))
                    .toList();
        }

        return ApiResponse.<List<MarketEventDto>>builder()
                .success(true)
                .data(events)
                .message("Market events fetched successfully")
                .build();
    }

    @GetMapping("/ipos")
    public ApiResponse<List<MarketEventDto>> getIpoEvents() {
        logger.info("GET /api/v1/market-events/ipos called");

        return ApiResponse.<List<MarketEventDto>>builder()
                .success(true)
                .data(marketEventQueryService.getIpoEvents())
                .message("IPO events fetched successfully")
                .build();
    }
}
