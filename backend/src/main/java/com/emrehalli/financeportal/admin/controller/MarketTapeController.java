package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.admin.markettape.dto.MarketTapeConfigResponse;
import com.emrehalli.financeportal.admin.markettape.service.MarketTapeService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets/tape")
@RequiredArgsConstructor
public class MarketTapeController {

    private final MarketTapeService marketTapeService;

    @GetMapping("/config")
    public ApiResponse<MarketTapeConfigResponse> getConfig() {
        return ApiResponse.<MarketTapeConfigResponse>builder()
                .success(true)
                .message("Market tape configuration fetched successfully")
                .data(marketTapeService.getConfig())
                .build();
    }

    @GetMapping("/quotes")
    public ApiResponse<List<MarketQueryService.MarketSnapshot>> getQuotes() {
        return ApiResponse.<List<MarketQueryService.MarketSnapshot>>builder()
                .success(true)
                .message("Market tape quotes fetched successfully")
                .data(marketTapeService.getQuotes())
                .build();
    }
}




