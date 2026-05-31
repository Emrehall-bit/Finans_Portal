package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.technicalanalysis.dto.IndicatorConfigDtos.Request;
import com.emrehalli.financeportal.technicalanalysis.dto.IndicatorConfigDtos.Response;
import com.emrehalli.financeportal.technicalanalysis.entity.IndicatorConfig;
import com.emrehalli.financeportal.technicalanalysis.repository.IndicatorConfigRepository;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
public class IndicatorConfigService {

    private final IndicatorConfigRepository indicatorConfigRepository;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final UserRepository userRepository;

    public IndicatorConfigService(IndicatorConfigRepository indicatorConfigRepository,
                                  MarketInstrumentRepository marketInstrumentRepository,
                                  UserRepository userRepository) {
        this.indicatorConfigRepository = indicatorConfigRepository;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Response> getActiveIndicators(String keycloakId, String instrumentCode) {
        User user = resolveUser(keycloakId);
        MarketInstrument instrument = resolveInstrument(instrumentCode);

        return indicatorConfigRepository
                .findByUserIdAndInstrumentIdAndIsActiveTrue(user.getId(), instrument.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Response saveIndicatorConfig(String keycloakId, String instrumentCode, Request request) {
        User user = resolveUser(keycloakId);
        MarketInstrument instrument = resolveInstrument(instrumentCode);

        IndicatorConfig config = IndicatorConfig.builder()
                .user(user)
                .instrument(instrument)
                .indicatorType(resolveIndicatorType(request.getIndicatorType()).name())
                .parameters(request.getParameters() != null ? request.getParameters() : new HashMap<>())
                .isActive(request.isActive())
                .build();

        return toResponse(indicatorConfigRepository.save(config));
    }

    @Transactional
    public void deleteIndicatorConfig(String keycloakId, Long id) {
        User user = resolveUser(keycloakId);
        indicatorConfigRepository.deleteByIdAndUserId(id, user.getId());
    }

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanici bulunamadi: keycloakId=" + keycloakId));
    }

    private MarketInstrument resolveInstrument(String instrumentCode) {
        return marketInstrumentRepository.findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstruman bulunamadi: " + instrumentCode));
    }

    private IndicatorType resolveIndicatorType(String value) {
        return IndicatorType.fromValue(value)
                .orElseThrow(() -> new TechnicalAnalysisException.Validation("Unsupported indicator: " + value));
    }

    private Response toResponse(IndicatorConfig config) {
        return Response.builder()
                .id(config.getId())
                .indicatorType(config.getIndicatorType())
                .parameters(config.getParameters())
                .isActive(config.isActive())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
