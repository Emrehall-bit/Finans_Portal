package com.emrehalli.financeportal.premium.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.premium.dto.PremiumSubscriptionResponse;
import com.emrehalli.financeportal.premium.service.PremiumSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Premium Abonelik", description = "Premium abonelik yükseltme, durum sorgulama ve iptal işlemleri")
@RestController
@RequestMapping("/api/v1/premium")
public class PremiumController {

    private final PremiumSubscriptionService premiumSubscriptionService;

    public PremiumController(PremiumSubscriptionService premiumSubscriptionService) {
        this.premiumSubscriptionService = premiumSubscriptionService;
    }

    @Operation(summary = "Premium yükseltme başlat")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Premium yükseltme iş akışı başlatıldı"))
    @PostMapping("/upgrade")
    public ApiResponse<PremiumSubscriptionResponse> requestUpgrade() {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.requestUpgrade())
                .message("Premium upgrade workflow started")
                .build();
    }

    @Operation(summary = "Premium durumunu sorgula")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Premium durumu başarıyla getirildi"))
    @GetMapping("/status")
    public ApiResponse<PremiumSubscriptionResponse> getStatus() {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.getCurrentStatus())
                .message("Premium status fetched")
                .build();
    }

    @Operation(summary = "Premium aboneliği iptal et")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Premium abonelik iptal edildi"))
    @PostMapping("/cancel")
    public ApiResponse<PremiumSubscriptionResponse> cancelUpgrade() {
        return ApiResponse.<PremiumSubscriptionResponse>builder()
                .success(true)
                .data(premiumSubscriptionService.cancelCurrentUpgrade())
                .message("Premium upgrade workflow cancelled")
                .build();
    }
}

