package com.emrehalli.financeportal.analysis.service;

import com.emrehalli.financeportal.analysis.dto.DrawingRequest;
import com.emrehalli.financeportal.analysis.dto.DrawingResponse;
import com.emrehalli.financeportal.analysis.entity.ChartDrawing;
import com.emrehalli.financeportal.analysis.exception.DrawingNotFoundException;
import com.emrehalli.financeportal.analysis.exception.PremiumRequiredException;
import com.emrehalli.financeportal.analysis.repository.ChartDrawingRepository;
import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.repository.AlertRepository;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
        logger.info("Çizim kaydediliyor: keycloakId={}, instrument={}, type={}", keycloakId, instrumentCode, req.getDrawingType());

        boolean isPremiumType = PREMIUM_DRAWING_TYPES.contains(req.getDrawingType().toUpperCase());
        if (isPremiumType && userRole != UserRole.USER_PREMIUM && userRole != UserRole.ADMIN) {
            logger.info("Premium çizim tipi reddedildi: type={}, role={}", req.getDrawingType(), userRole);
            throw new PremiumRequiredException("Bu çizim tipi Premium üyelik gerektirir: " + req.getDrawingType());
        }

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: keycloakId=" + keycloakId));
        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        ChartDrawing drawing = ChartDrawing.builder()
                .user(user)
                .instrument(instrument)
                .timeframe(req.getTimeframe())
                .drawingType(req.getDrawingType().toUpperCase())
                .points(req.getPoints())
                .style(req.getStyle() != null ? req.getStyle() : Map.of())
                .label(req.getLabel())
                .isPremiumFeature(isPremiumType)
                .isAlertLinked(false)
                .build();

        ChartDrawing saved = chartDrawingRepository.save(drawing);
        logger.info("Çizim kaydedildi: id={}, type={}", saved.getId(), saved.getDrawingType());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DrawingResponse> getDrawings(String keycloakId, String instrumentCode, String timeframe) {
        logger.info("Çizimler getiriliyor: keycloakId={}, instrument={}, timeframe={}", keycloakId, instrumentCode, timeframe);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: keycloakId=" + keycloakId));
        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        return chartDrawingRepository
                .findByUserIdAndInstrumentIdAndTimeframe(user.getId(), instrument.getId(), timeframe)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DrawingResponse updateDrawing(String keycloakId, Long drawingId, DrawingRequest req) {
        logger.info("Çizim güncelleniyor: keycloakId={}, drawingId={}", keycloakId, drawingId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: keycloakId=" + keycloakId));

        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        drawing.setPoints(req.getPoints());
        if (req.getStyle() != null) drawing.setStyle(req.getStyle());
        if (req.getLabel() != null) drawing.setLabel(req.getLabel());

        ChartDrawing saved = chartDrawingRepository.save(drawing);

        // Alert bağlıysa koordinat güncelle
        if (saved.isAlertLinked() && saved.getLinkedAlertId() != null) {
            portfolioAlertIntegrationService.syncDrawingWithAlert(saved);
        }

        logger.info("Çizim güncellendi: id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void deleteDrawing(String keycloakId, Long drawingId) {
        logger.info("Çizim siliniyor: keycloakId={}, drawingId={}", keycloakId, drawingId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: keycloakId=" + keycloakId));

        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        chartDrawingRepository.delete(drawing);
        logger.info("Çizim silindi: id={}", drawingId);
    }

    @Transactional
    public DrawingResponse linkDrawingToAlert(String keycloakId, Long drawingId, Long alertId) {
        logger.info("Çizim alert'e bağlanıyor: keycloakId={}, drawingId={}, alertId={}", keycloakId, drawingId, alertId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: keycloakId=" + keycloakId));

        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        Alert alert = alertRepository.findByIdAndUserId(alertId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Alert bulunamadı: id=" + alertId));

        drawing.setAlertLinked(true);
        drawing.setLinkedAlertId(alert.getId());

        ChartDrawing saved = chartDrawingRepository.save(drawing);
        portfolioAlertIntegrationService.syncDrawingWithAlert(saved);

        logger.info("Çizim alert'e bağlandı: drawingId={}, alertId={}", drawingId, alertId);
        return toResponse(saved);
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
