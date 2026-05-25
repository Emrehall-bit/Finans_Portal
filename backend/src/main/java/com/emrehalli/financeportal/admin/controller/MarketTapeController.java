package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.admin.markettape.dto.MarketTapeConfigResponse;
import com.emrehalli.financeportal.admin.markettape.service.MarketTapeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}



