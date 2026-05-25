package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.premium.dto.PremiumSubscriptionResponse;
import com.emrehalli.financeportal.premium.service.PremiumSubscriptionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/premium")
public class AdminPremiumController {

    private final PremiumSubscriptionService premiumSubscriptionService;

    public AdminPremiumController(PremiumSubscriptionService premiumSubscriptionService) {
        this.premiumSubscriptionService = premiumSubscriptionService;
    }

    @GetMapping("/pending")
    public ApiResponse<List<PremiumSubscriptionResponse>> getPendingSubscriptions() {
        return ApiResponse.<List<PremiumSubscriptionResponse>>builder()
                .success(true)
                .data(premiumSubscriptionService.getPendingSubscriptions())
                .message("Pending premium subscriptions fetched")
                .build();
    }

    @PostMapping("/{subscriptionId}/payment-success")
    public ApiResponse<PremiumSubscriptionResponse> markPaymentSuccess(@PathVariable Long subscriptionId) {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.markPaymentSuccess(subscriptionId))
                .message("Premium payment marked as success")
                .build();
    }

    @PostMapping("/{subscriptionId}/payment-fail")
    public ApiResponse<PremiumSubscriptionResponse> markPaymentFail(@PathVariable Long subscriptionId) {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.markPaymentFail(subscriptionId))
                .message("Premium payment marked as failed")
                .build();
    }
}



