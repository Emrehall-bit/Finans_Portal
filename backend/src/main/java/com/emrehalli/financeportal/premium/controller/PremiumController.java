package com.emrehalli.financeportal.premium.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.premium.dto.PremiumSubscriptionResponse;
import com.emrehalli.financeportal.premium.service.PremiumSubscriptionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/premium")
public class PremiumController {

    private final PremiumSubscriptionService premiumSubscriptionService;

    public PremiumController(PremiumSubscriptionService premiumSubscriptionService) {
        this.premiumSubscriptionService = premiumSubscriptionService;
    }

    @PostMapping("/upgrade")
    public ApiResponse<PremiumSubscriptionResponse> requestUpgrade() {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.requestUpgrade())
                .message("Premium upgrade workflow started")
                .build();
    }

    @GetMapping("/status")
    public ApiResponse<PremiumSubscriptionResponse> getStatus() {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.getCurrentStatus())
                .message("Premium status fetched")
                .build();
    }

    @PostMapping("/cancel")
    public ApiResponse<PremiumSubscriptionResponse> cancelUpgrade() {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.cancelCurrentUpgrade())
                .message("Premium upgrade workflow cancelled")
                .build();
    }
}




