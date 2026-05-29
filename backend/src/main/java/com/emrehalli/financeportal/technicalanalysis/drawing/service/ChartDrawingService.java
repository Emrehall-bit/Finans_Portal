package com.emrehalli.financeportal.technicalanalysis.drawing.service;

import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingRequest;
import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingResponse;
import com.emrehalli.financeportal.technicalanalysis.drawing.entity.ChartDrawing;
import com.emrehalli.financeportal.technicalanalysis.exception.DrawingNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.exception.PremiumRequiredException;
import com.emrehalli.financeportal.technicalanalysis.drawing.repository.ChartDrawingRepository;
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
        logger.info("Ã‡izim kaydediliyor: keycloakId={}, instrument={}, type={}", keycloakId, instrumentCode, req.getDrawingType());

        boolean isPremiumType = PREMIUM_DRAWING_TYPES.contains(req.getDrawingType().toUpperCase());
        if (isPremiumType && userRole != UserRole.USER_PREMIUM && userRole != UserRole.ADMIN) {
            logger.info("Premium Ã§izim tipi reddedildi: type={}, role={}", req.getDrawingType(), userRole);
            throw new PremiumRequiredException("Bu Ã§izim tipi Premium Ã¼yelik gerektirir: " + req.getDrawingType());
        }

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("KullanÄ±cÄ± bulunamadÄ±: keycloakId=" + keycloakId));
        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("EnstrÃ¼man bulunamadÄ±: " + instrumentCode));

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
        logger.info("Ã‡izim kaydedildi: id={}, type={}", saved.getId(), saved.getDrawingType());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DrawingResponse> getDrawings(String keycloakId, String instrumentCode, String timeframe) {
        logger.info("Ã‡izimler getiriliyor: keycloakId={}, instrument={}, timeframe={}", keycloakId, instrumentCode, timeframe);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("KullanÄ±cÄ± bulunamadÄ±: keycloakId=" + keycloakId));
        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("EnstrÃ¼man bulunamadÄ±: " + instrumentCode));

        return chartDrawingRepository
                .findByUserIdAndInstrumentIdAndTimeframe(user.getId(), instrument.getId(), timeframe)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DrawingResponse updateDrawing(String keycloakId, Long drawingId, DrawingRequest req) {
        logger.info("Ã‡izim gÃ¼ncelleniyor: keycloakId={}, drawingId={}", keycloakId, drawingId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("KullanÄ±cÄ± bulunamadÄ±: keycloakId=" + keycloakId));

        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        drawing.setPoints(req.getPoints());
        if (req.getStyle() != null) drawing.setStyle(req.getStyle());
        if (req.getLabel() != null) drawing.setLabel(req.getLabel());

        ChartDrawing saved = chartDrawingRepository.save(drawing);

        // Alert baÄŸlÄ±ysa koordinat gÃ¼ncelle
        if (saved.isAlertLinked() && saved.getLinkedAlertId() != null) {
            portfolioAlertIntegrationService.syncDrawingWithAlert(saved);
        }

        logger.info("Ã‡izim gÃ¼ncellendi: id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void deleteDrawing(String keycloakId, Long drawingId) {
        logger.info("Ã‡izim siliniyor: keycloakId={}, drawingId={}", keycloakId, drawingId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("KullanÄ±cÄ± bulunamadÄ±: keycloakId=" + keycloakId));

        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        chartDrawingRepository.delete(drawing);
        logger.info("Ã‡izim silindi: id={}", drawingId);
    }

    @Transactional
    public DrawingResponse linkDrawingToAlert(String keycloakId, Long drawingId, Long alertId) {
        logger.info("Ã‡izim alert'e baÄŸlanÄ±yor: keycloakId={}, drawingId={}, alertId={}", keycloakId, drawingId, alertId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("KullanÄ±cÄ± bulunamadÄ±: keycloakId=" + keycloakId));

        ChartDrawing drawing = chartDrawingRepository.findByIdAndUserId(drawingId, user.getId())
                .orElseThrow(() -> new DrawingNotFoundException(drawingId));

        Alert alert = alertRepository.findByIdAndUserId(alertId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Alert bulunamadÄ±: id=" + alertId));

        drawing.setAlertLinked(true);
        drawing.setLinkedAlertId(alert.getId());

        ChartDrawing saved = chartDrawingRepository.save(drawing);
        portfolioAlertIntegrationService.syncDrawingWithAlert(saved);

        logger.info("Ã‡izim alert'e baÄŸlandÄ±: drawingId={}, alertId={}", drawingId, alertId);
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

