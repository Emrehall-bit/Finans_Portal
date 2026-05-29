package com.emrehalli.financeportal.technicalanalysis.drawing.service;

import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.repository.AlertRepository;
import com.emrehalli.financeportal.technicalanalysis.drawing.entity.ChartDrawing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioAlertIntegrationService {

    private static final Logger logger = LogManager.getLogger(PortfolioAlertIntegrationService.class);

    private final AlertRepository alertRepository;

    public PortfolioAlertIntegrationService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Ã‡izimin ilk noktasÄ±ndaki fiyatÄ±, baÄŸlÄ± alert'in target_price deÄŸerine yazar.
     * Ã‡izim sÃ¼rÃ¼klenince alert seviyesi de gÃ¼ncellenir.
     */
    @Transactional
    public void syncDrawingWithAlert(ChartDrawing drawing) {
        if (!drawing.isAlertLinked() || drawing.getLinkedAlertId() == null) {
            return;
        }

        BigDecimal drawingPrice = extractFirstPrice(drawing.getPoints());
        if (drawingPrice == null) {
            logger.warn("Ã‡izim fiyatÄ± Ã§Ä±karÄ±lamadÄ±: drawingId={}", drawing.getId());
            return;
        }

        alertRepository.findById(drawing.getLinkedAlertId()).ifPresent(alert -> {
            logger.info("Alert hedef fiyatÄ± gÃ¼ncelleniyor: alertId={}, eskiFiyat={}, yeniFiyat={}",
                    alert.getId(), alert.getTargetPrice(), drawingPrice);
            alert.setTargetPrice(drawingPrice);
            alertRepository.save(alert);
        });
    }

    private BigDecimal extractFirstPrice(List<Map<String, Object>> points) {
        if (points == null || points.isEmpty()) return null;
        Map<String, Object> firstPoint = points.get(0);
        Object priceObj = firstPoint.get("price");
        if (priceObj == null) return null;
        try {
            return new BigDecimal(priceObj.toString());
        } catch (NumberFormatException e) {
            logger.error("Ã‡izim noktasÄ±ndan fiyat ayrÄ±ÅŸtÄ±rÄ±lamadÄ±: value={}", priceObj);
            return null;
        }
    }
}

