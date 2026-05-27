package com.emrehalli.financeportal.technicalanalysis.controller;

import com.emrehalli.financeportal.analysis.dto.IndicatorConfigRequest;
import com.emrehalli.financeportal.analysis.dto.IndicatorConfigResponse;
import com.emrehalli.financeportal.analysis.entity.IndicatorConfig;
import com.emrehalli.financeportal.analysis.repository.IndicatorConfigRepository;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
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

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/v1/technical-analysis")
public class TechnicalIndicatorConfigController {

    private static final Logger logger = LogManager.getLogger(TechnicalIndicatorConfigController.class);

    private final IndicatorConfigRepository indicatorConfigRepository;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;

    public TechnicalIndicatorConfigController(IndicatorConfigRepository indicatorConfigRepository,
                                              MarketInstrumentRepository marketInstrumentRepository,
                                              UserRepository userRepository,
                                              CurrentUserResolver currentUserResolver) {
        this.indicatorConfigRepository = indicatorConfigRepository;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/{instrumentCode}/indicators")
    public ApiResponse<List<IndicatorConfigResponse>> getUserIndicators(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gosterge konfigurasyonlari getiriliyor: keycloakId={}, instrument={}", currentUser.keycloakId(), instrumentCode);

        User user = resolveUser(currentUser.keycloakId());
        MarketInstrument instrument = resolveInstrument(instrumentCode);

        List<IndicatorConfigResponse> configs = indicatorConfigRepository
                .findByUserIdAndInstrumentIdAndIsActiveTrue(user.getId(), instrument.getId())
                .stream()
                .map(this::toIndicatorResponse)
                .toList();

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

        User user = resolveUser(currentUser.keycloakId());
        MarketInstrument instrument = resolveInstrument(instrumentCode);

        IndicatorConfig config = IndicatorConfig.builder()
                .user(user)
                .instrument(instrument)
                .indicatorType(req.getIndicatorType().toUpperCase())
                .parameters(req.getParameters() != null ? req.getParameters() : new HashMap<>())
                .isActive(req.isActive())
                .build();

        IndicatorConfig saved = indicatorConfigRepository.save(config);
        return ApiResponse.<IndicatorConfigResponse>builder()
                .success(true)
                .data(toIndicatorResponse(saved))
                .message("Gosterge konfigurasyonu kaydedildi")
                .build();
    }

    @DeleteMapping("/indicators/{id}")
    public ApiResponse<Void> deleteIndicatorConfig(@PathVariable Long id) {
        CurrentUser currentUser = currentUserResolver.resolve();
        User user = resolveUser(currentUser.keycloakId());
        logger.info("Gosterge konfigurasyonu siliniyor: keycloakId={}, configId={}", currentUser.keycloakId(), id);

        indicatorConfigRepository.deleteByIdAndUserId(id, user.getId());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Gosterge konfigurasyonu silindi")
                .build();
    }

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanici bulunamadi: keycloakId=" + keycloakId));
    }

    private MarketInstrument resolveInstrument(String instrumentCode) {
        return marketInstrumentRepository.findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstruman bulunamadi: " + instrumentCode));
    }

    private IndicatorConfigResponse toIndicatorResponse(IndicatorConfig config) {
        return IndicatorConfigResponse.builder()
                .id(config.getId())
                .indicatorType(config.getIndicatorType())
                .parameters(config.getParameters())
                .isActive(config.isActive())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
