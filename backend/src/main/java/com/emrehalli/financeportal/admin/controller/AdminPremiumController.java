package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.premium.dto.PremiumSubscriptionResponse;
import com.emrehalli.financeportal.premium.service.PremiumSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/premium")
@Tag(name = "Admin - Premium Yonetimi", description = "Premium abonelik talep onaylama islemleri")
public class AdminPremiumController {

    private final PremiumSubscriptionService premiumSubscriptionService;

    public AdminPremiumController(PremiumSubscriptionService premiumSubscriptionService) {
        this.premiumSubscriptionService = premiumSubscriptionService;
    }

    @Operation(summary = "Bekleyen premium talepleri listele", description = "Odeme onayini bekleyen premium abonelik taleplerini listeler")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bekleyen talepler basariyla listelendi"))
    @GetMapping("/pending")
    public ApiResponse<List<PremiumSubscriptionResponse>> getPendingSubscriptions() {
        return ApiResponse.<List<PremiumSubscriptionResponse>>builder()
                .success(true)
                .data(premiumSubscriptionService.getPendingSubscriptions())
                .message("Pending premium subscriptions fetched")
                .build();
    }

    @Operation(summary = "Odemeyi onayla", description = "Belirtilen abonelik icin basarili odemeyi onaylar ve premium erisimi aktif eder")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Odeme basariyla onaylandi"))
    @PostMapping("/{subscriptionId}/payment-success")
    public ApiResponse<PremiumSubscriptionResponse> markPaymentSuccess(@PathVariable Long subscriptionId) {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.markPaymentSuccess(subscriptionId))
                .message("Premium payment marked as success")
                .build();
    }

    @Operation(summary = "Odemeyi reddet", description = "Belirtilen abonelik icin basarisiz odemeyi kaydeder")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Odeme basarisiz olarak isaretlendi"))
    @PostMapping("/{subscriptionId}/payment-fail")
    public ApiResponse<PremiumSubscriptionResponse> markPaymentFail(@PathVariable Long subscriptionId) {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.markPaymentFail(subscriptionId))
                .message("Premium payment marked as failed")
                .build();
    }
}

