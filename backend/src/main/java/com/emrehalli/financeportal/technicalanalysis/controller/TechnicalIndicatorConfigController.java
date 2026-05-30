package com.emrehalli.financeportal.technicalanalysis.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.technicalanalysis.config.dto.IndicatorConfigRequest;
import com.emrehalli.financeportal.technicalanalysis.config.dto.IndicatorConfigResponse;
import com.emrehalli.financeportal.technicalanalysis.config.service.IndicatorConfigService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/technical-analysis")
public class TechnicalIndicatorConfigController {

    private static final Logger logger = LogManager.getLogger(TechnicalIndicatorConfigController.class);

    private final IndicatorConfigService indicatorConfigService;
    private final CurrentUserResolver currentUserResolver;

    public TechnicalIndicatorConfigController(IndicatorConfigService indicatorConfigService,
                                              CurrentUserResolver currentUserResolver) {
        this.indicatorConfigService = indicatorConfigService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/{instrumentCode}/indicators")
    public ApiResponse<List<IndicatorConfigResponse>> getUserIndicators(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gosterge konfigurasyonlari getiriliyor: keycloakId={}, instrument={}",
                currentUser.keycloakId(), instrumentCode);

        List<IndicatorConfigResponse> configs = indicatorConfigService
                .getActiveIndicators(currentUser.keycloakId(), instrumentCode);

        return ApiResponse.<List<IndicatorConfigResponse>>builder()
                .success(true)
                .data(configs)
                .message("Gosterge konfigurasyonlari getirildi")
                .build();
    }

    @PostMapping("/{instrumentCode}/indicators")
    public ApiResponse<IndicatorConfigResponse> saveIndicatorConfig(@PathVariable String instrumentCode,
                                                                    @Valid @RequestBody IndicatorConfigRequest req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gosterge konfigurasyonu kaydediliyor: keycloakId={}, instrument={}, type={}",
                currentUser.keycloakId(), instrumentCode, req.getIndicatorType());

        IndicatorConfigResponse saved = indicatorConfigService
                .saveIndicatorConfig(currentUser.keycloakId(), instrumentCode, req);

        return ApiResponse.<IndicatorConfigResponse>builder()
                .success(true)
                .data(saved)
                .message("Gosterge konfigurasyonu kaydedildi")
                .build();
    }

    @DeleteMapping("/indicators/{id}")
    public ApiResponse<Void> deleteIndicatorConfig(@PathVariable Long id) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gosterge konfigurasyonu siliniyor: keycloakId={}, configId={}", currentUser.keycloakId(), id);

        indicatorConfigService.deleteIndicatorConfig(currentUser.keycloakId(), id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Gosterge konfigurasyonu silindi")
                .build();
    }
}
