package com.emrehalli.financeportal.technicalanalysis.drawing.service;

import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.repository.AlertRepository;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingRequest;
import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingResponse;
import com.emrehalli.financeportal.technicalanalysis.drawing.entity.ChartDrawing;
import com.emrehalli.financeportal.technicalanalysis.drawing.repository.ChartDrawingRepository;
import com.emrehalli.financeportal.technicalanalysis.exception.DrawingNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.exception.PremiumRequiredException;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ChartDrawingService {

    private static final Logger logger = LogManager.getLogger(ChartDrawingService.class);
    private static final Set<String> PREMIUM_DRAWING_TYPES = Set.of(
            "FIBONACCI_RETRACEMENT", "FIBONACCI_EXTENSION", "PITCHFORK"
    );

    private final ChartDrawingRepository chartDrawingRepository;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final UserRepository userRepository;
    private final AlertRepository alertRepository;
    private final PortfolioAlertIntegrationService portfolioAlertIntegrationService;

    public ChartDrawingService(ChartDrawingRepository chartDrawingRepository,
                               MarketInstrumentRepository marketInstrumentRepository,
                               UserRepository userRepository,
                               AlertRepository alertRepository,
                               PortfolioAlertIntegrationService portfolioAlertIntegrationService) {
        this.chartDrawingRepository = chartDrawingRepository;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
        this.portfolioAlertIntegrationService = portfolioAlertIntegrationService;
    }

    @Transactional
    public DrawingResponse saveDrawing(String keycloakId, UserRole userRole, String instrumentCode, DrawingRequest req) {
        String drawingType = normalizeDrawingType(req.getDrawingType());
        logger.info("Cizim kaydediliyor: keycloakId={}, instrument={}, type={}", keycloakId, instrumentCode, drawingType);

        boolean isPremiumType = isPremiumDrawingType(drawingType);
        if (isPremiumType && userRole != UserRole.USER_PREMIUM && userRole != UserRole.ADMIN) {
            logger.info("Premium cizim tipi reddedildi: type={}, role={}", drawingType, userRole);
            throw new PremiumRequiredException("Bu cizim tipi Premium uyelik gerektirir: " + drawingType);
        }

        User user = resolveUser(keycloakId);
        MarketInstrument instrument = resolveInstrument(instrumentCode);

        ChartDrawing drawing = ChartDrawing.builder()
                .user(user)
                .instrument(instrument)
                .timeframe(req.getTimeframe())
                .drawingType(drawingType)
                .points(req.getPoints())
                .style(req.getStyle() != null ? req.getStyle() : Map.of())
                .label(req.getLabel())
                .isPremiumFeature(isPremiumType)
                .isAlertLinked(false)
                .build();

        ChartDrawing saved = chartDrawingRepository.save(drawing);
        logger.info("Cizim kaydedildi: id={}, type={}", saved.getId(), saved.getDrawingType());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DrawingResponse> getDrawings(String keycloakId, String instrumentCode, String timeframe) {
        User user = resolveUser(keycloakId);
        MarketInstrument instrument = resolveInstrument(instrumentCode);

        return chartDrawingRepository
                .findByUserIdAndInstrumentIdAndTimeframe(user.getId(), instrument.getId(), timeframe)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DrawingResponse updateDrawing(String keycloakId, Long drawingId, DrawingRequest req) {
        logger.info("Cizim guncelleniyor: keycloakId={}, drawingId={}", keycloakId, drawingId);

        User user = resolveUser(keycloakId);
        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        drawing.setPoints(req.getPoints());
        if (req.getStyle() != null) drawing.setStyle(req.getStyle());
        if (req.getLabel() != null) drawing.setLabel(req.getLabel());

        ChartDrawing saved = chartDrawingRepository.save(drawing);
        if (saved.isAlertLinked() && saved.getLinkedAlertId() != null) {
            portfolioAlertIntegrationService.syncDrawingWithAlert(saved);
        }

        logger.info("Cizim guncellendi: id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void deleteDrawing(String keycloakId, Long drawingId) {
        logger.info("Cizim siliniyor: keycloakId={}, drawingId={}", keycloakId, drawingId);

        User user = resolveUser(keycloakId);
        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        chartDrawingRepository.delete(drawing);
        logger.info("Cizim silindi: id={}", drawingId);
    }

    @Transactional
    public DrawingResponse linkDrawingToAlert(String keycloakId, Long drawingId, Long alertId) {
        logger.info("Cizim alert'e baglaniyor: keycloakId={}, drawingId={}, alertId={}", keycloakId, drawingId, alertId);

        User user = resolveUser(keycloakId);
        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        Alert alert = alertRepository.findByIdAndUserId(alertId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Alert bulunamadi: id=" + alertId));

        drawing.setAlertLinked(true);
        drawing.setLinkedAlertId(alert.getId());

        ChartDrawing saved = chartDrawingRepository.save(drawing);
        portfolioAlertIntegrationService.syncDrawingWithAlert(saved);

        logger.info("Cizim alert'e baglandi: drawingId={}, alertId={}", drawingId, alertId);
        return toResponse(saved);
    }

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanici bulunamadi: keycloakId=" + keycloakId));
    }

    private MarketInstrument resolveInstrument(String instrumentCode) {
        return marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstruman bulunamadi: " + instrumentCode));
    }

    private String normalizeDrawingType(String drawingType) {
        if (drawingType == null || drawingType.isBlank()) {
            throw new TechnicalAnalysisException.Validation("drawingType cannot be blank");
        }
        return drawingType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPremiumDrawingType(String drawingType) {
        return PREMIUM_DRAWING_TYPES.contains(drawingType);
    }

    private DrawingResponse toResponse(ChartDrawing d) {
        return DrawingResponse.builder()
                .id(d.getId())
                .instrumentId(d.getInstrument() != null ? d.getInstrument().getId() : null)
                .timeframe(d.getTimeframe())
                .drawingType(d.getDrawingType())
                .points(d.getPoints())
                .style(d.getStyle())
                .label(d.getLabel())
                .isAlertLinked(d.isAlertLinked())
                .linkedAlertId(d.getLinkedAlertId())
                .isPremiumFeature(d.isPremiumFeature())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
