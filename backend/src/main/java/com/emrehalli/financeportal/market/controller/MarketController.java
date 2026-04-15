package com.emrehalli.financeportal.market.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketController {

    private static final Logger logger = LogManager.getLogger(MarketController.class);

    private final MarketQueryService marketQueryService;

    public MarketController(MarketQueryService marketQueryService) {
        this.marketQueryService = marketQueryService;
    }

    @GetMapping("/tcmb")
    public ApiResponse<List<MarketDataDto>> getTcmbData() {
        logger.info("GET /api/v1/markets/tcmb called");

        List<MarketDataDto> data = marketQueryService.getTcmbMarketData();

        if (data == null) {
            logger.warn("MarketQueryService returned null for TCMB data");
            data = List.of();
        }

        logger.info("Returning {} TCMB records", data.size());

        return ApiResponse.<List<MarketDataDto>>builder()
                .success(true)
                .data(data)
                .message("TCMB market data fetched successfully")
                .build();
    }
}