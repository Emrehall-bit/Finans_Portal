package com.emrehalli.financeportal.markettape.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.markettape.dto.MarketTapeConfigResponse;
import com.emrehalli.financeportal.markettape.dto.UpdateMarketTapeConfigRequest;
import com.emrehalli.financeportal.markettape.service.MarketTapeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/markets/tape")
@RequiredArgsConstructor
public class AdminMarketTapeController {

    private final MarketTapeService marketTapeService;

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MarketTapeConfigResponse> updateConfig(@Valid @RequestBody UpdateMarketTapeConfigRequest request) {
        return ApiResponse.<MarketTapeConfigResponse>builder()
                .success(true)
                .message("Market tape configuration updated successfully")
                .data(marketTapeService.updateConfig(request.symbols()))
                .build();
    }
}
