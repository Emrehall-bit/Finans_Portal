package com.emrehalli.financeportal.technicalanalysis.fundamental.scheduler;

import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.service.FundamentalAnalysisService;
import com.emrehalli.financeportal.technicalanalysis.drawing.service.PortfolioAlertIntegrationService;
import com.emrehalli.financeportal.technicalanalysis.drawing.repository.ChartDrawingRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FundamentalRatiosScheduler {

    private static final Logger logger = LogManager.getLogger(FundamentalRatiosScheduler.class);

    private final FundamentalAnalysisService fundamentalAnalysisService;
    private final CompanyFinancialsRepository companyFinancialsRepository;
    private final ChartDrawingRepository chartDrawingRepository;
    private final PortfolioAlertIntegrationService portfolioAlertIntegrationService;

    public FundamentalRatiosScheduler(FundamentalAnalysisService fundamentalAnalysisService,
                                       CompanyFinancialsRepository companyFinancialsRepository,
                                       ChartDrawingRepository chartDrawingRepository,
                                       PortfolioAlertIntegrationService portfolioAlertIntegrationService) {
        this.fundamentalAnalysisService = fundamentalAnalysisService;
        this.companyFinancialsRepository = companyFinancialsRepository;
        this.chartDrawingRepository = chartDrawingRepository;
        this.portfolioAlertIntegrationService = portfolioAlertIntegrationService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void recalculateFundamentalRatios() {
        logger.info("Temel analiz oranları gece hesaplama başladı");

        // Finansal verisi olan benzersiz enstrüman ID'lerini doğrudan DB'den al (entity hydration yok).
        List<Long> instrumentIds = companyFinancialsRepository.findDistinctInstrumentIds();

        int success = 0;
        int errors = 0;
        for (Long instrumentId : instrumentIds) {
            try {
                fundamentalAnalysisService.calculateRatios(instrumentId, null);
                success++;
            } catch (Exception e) {
                logger.error("Temel analiz hesaplama hatası: instrumentId={}, hata={}", instrumentId, e.getMessage(), e);
                errors++;
            }
        }

        logger.info("Temel analiz oranları güncellendi: başarılı={}, hata={}, toplam={}", success, errors, instrumentIds.size());
    }

    @Scheduled(fixedDelay = 60_000)
    public void checkAlertThresholds() {
        List<?> linkedDrawings = chartDrawingRepository.findByIsAlertLinkedTrueAndLinkedAlertIdIsNotNull();
        if (linkedDrawings.isEmpty()) return;

        logger.info("Alert bağlı çizimler kontrol ediliyor: adet={}", linkedDrawings.size());

        linkedDrawings.forEach(d -> {
            try {
                portfolioAlertIntegrationService.syncDrawingWithAlert(
                        (com.emrehalli.financeportal.technicalanalysis.drawing.entity.ChartDrawing) d);
            } catch (Exception e) {
                logger.error("Alert senkronizasyon hatası: hata={}", e.getMessage());
            }
        });
    }
}

