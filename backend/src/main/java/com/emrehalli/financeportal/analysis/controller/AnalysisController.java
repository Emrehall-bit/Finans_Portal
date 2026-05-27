package com.emrehalli.financeportal.analysis.controller;

import com.emrehalli.financeportal.analysis.dto.*;
import com.emrehalli.financeportal.analysis.entity.CompanyFinancials;
import com.emrehalli.financeportal.analysis.entity.FundamentalRatios;
import com.emrehalli.financeportal.analysis.exception.PremiumRequiredException;
import com.emrehalli.financeportal.analysis.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.analysis.repository.FundamentalHistoryRepository;
import com.emrehalli.financeportal.analysis.repository.FundamentalRatiosRepository;
import com.emrehalli.financeportal.analysis.service.ChartDrawingService;
import com.emrehalli.financeportal.analysis.service.FundamentalAnalysisService;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.config.security.RequiresPremium;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.user.entity.UserRole;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Logger logger = LogManager.getLogger(AnalysisController.class);
    private final ChartDrawingService chartDrawingService;
    private final FundamentalAnalysisService fundamentalAnalysisService;
    private final FundamentalRatiosRepository fundamentalRatiosRepository;
    private final FundamentalHistoryRepository fundamentalHistoryRepository;
    private final CompanyFinancialsRepository companyFinancialsRepository;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final CurrentUserResolver currentUserResolver;

    public AnalysisController(ChartDrawingService chartDrawingService,
                              FundamentalAnalysisService fundamentalAnalysisService,
                              FundamentalRatiosRepository fundamentalRatiosRepository,
                              FundamentalHistoryRepository fundamentalHistoryRepository,
                              CompanyFinancialsRepository companyFinancialsRepository,
                              MarketInstrumentRepository marketInstrumentRepository,
                              CurrentUserResolver currentUserResolver) {
        this.chartDrawingService = chartDrawingService;
        this.fundamentalAnalysisService = fundamentalAnalysisService;
        this.fundamentalRatiosRepository = fundamentalRatiosRepository;
        this.fundamentalHistoryRepository = fundamentalHistoryRepository;
        this.companyFinancialsRepository = companyFinancialsRepository;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.currentUserResolver = currentUserResolver;
    }

    // ── Teknik Analiz ─────────────────────────────────────────────────────────

    @GetMapping("/drawings/{instrumentCode}")
    public ApiResponse<List<DrawingResponse>> getDrawings(
            @PathVariable String instrumentCode,
            @RequestParam(defaultValue = "1d") String timeframe) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Çizimler getiriliyor: keycloakId={}, instrument={}", currentUser.keycloakId(), instrumentCode);

        List<DrawingResponse> drawings = chartDrawingService.getDrawings(currentUser.keycloakId(), instrumentCode, timeframe);
        return ApiResponse.<List<DrawingResponse>>builder()
                .success(true)
                .data(drawings)
                .message("Çizimler getirildi")
                .build();
    }

    @PostMapping("/drawings/{instrumentCode}")
    public ApiResponse<DrawingResponse> saveDrawing(
            @PathVariable String instrumentCode,
            @Valid @RequestBody DrawingRequest req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        DrawingResponse drawing = chartDrawingService.saveDrawing(
                currentUser.keycloakId(), currentUser.role(), instrumentCode, req);

        return ApiResponse.<DrawingResponse>builder()
                .success(true)
                .data(drawing)
                .message("Çizim kaydedildi")
                .build();
    }

    @PatchMapping("/drawings/{id}")
    public ApiResponse<DrawingResponse> updateDrawing(
            @PathVariable Long id,
            @Valid @RequestBody DrawingRequest req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        DrawingResponse drawing = chartDrawingService.updateDrawing(currentUser.keycloakId(), id, req);

        return ApiResponse.<DrawingResponse>builder()
                .success(true)
                .data(drawing)
                .message("Çizim güncellendi")
                .build();
    }

    @DeleteMapping("/drawings/{id}")
    public ApiResponse<Void> deleteDrawing(@PathVariable Long id) {
        CurrentUser currentUser = currentUserResolver.resolve();
        chartDrawingService.deleteDrawing(currentUser.keycloakId(), id);

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Çizim silindi")
                .build();
    }

    @PostMapping("/drawings/{id}/link-alert")
    @RequiresPremium
    public ApiResponse<DrawingResponse> linkDrawingToAlert(
            @PathVariable Long id,
            @Valid @RequestBody LinkAlertRequest req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Çizim alert'e bağlanıyor: keycloakId={}, drawingId={}, alertId={}",
                currentUser.keycloakId(), id, req.getAlertId());

        DrawingResponse drawing = chartDrawingService.linkDrawingToAlert(
                currentUser.keycloakId(), id, req.getAlertId());

        return ApiResponse.<DrawingResponse>builder()
                .success(true)
                .data(drawing)
                .message("Çizim alert'e bağlandı")
                .build();
    }

    // ── Temel Analiz ─────────────────────────────────────────────────────────

    @GetMapping("/fundamental/{instrumentCode}")
    public ApiResponse<FundamentalRatiosResponse> getFundamentalRatios(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Temel analiz oranları getiriliyor: instrument={}, role={}", instrumentCode, currentUser.role());

        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        FundamentalRatios ratios = fundamentalRatiosRepository
                .findTopByInstrumentIdOrderByCalculatedAtDesc(instrument.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Temel analiz verisi bulunamadı: " + instrumentCode));

        boolean isPremium = currentUser.role() == UserRole.USER_PREMIUM || currentUser.role() == UserRole.ADMIN;

        FundamentalRatiosResponse response = FundamentalRatiosResponse.builder()
                .period(ratios.getPeriod())
                .calculationPrice(ratios.getCalculationPrice())
                .peRatio(ratios.getPeRatio())
                .pbRatio(ratios.getPbRatio())
                .grossMargin(ratios.getGrossMargin())
                .netMargin(ratios.getNetMargin())
                .roe(ratios.getRoe())
                .roa(ratios.getRoa())
                .revenueGrowthYoy(ratios.getRevenueGrowthYoy())
                .netIncomeGrowthYoy(ratios.getNetIncomeGrowthYoy())
                .assetGrowthYoy(ratios.getAssetGrowthYoy())
                .debtToEquity(ratios.getDebtToEquity())
                .currentRatio(ratios.getCurrentRatio())
                .overallSignal(ratios.getOverallSignal())
                // Premium alanlar: null döner, frontend blur gösterir
                .grahamNumber(isPremium ? ratios.getGrahamNumber() : null)
                .piotroskiScore(isPremium ? ratios.getPiotroskiScore() : null)
                .altmanZScore(isPremium ? ratios.getAltmanZScore() : null)
                .premiumRequired(!isPremium)
                .calculatedAt(ratios.getCalculatedAt())
                .build();

        return ApiResponse.<FundamentalRatiosResponse>builder()
                .success(true)
                .data(response)
                .message("Temel analiz oranları getirildi")
                .build();
    }

    @GetMapping("/fundamental/{instrumentCode}/history")
    @RequiresPremium
    public ApiResponse<List<FundamentalHistoryPoint>> getFundamentalHistory(@PathVariable String instrumentCode) {
        logger.info("Temel analiz geçmişi getiriliyor: instrument={}", instrumentCode);

        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        List<FundamentalHistoryPoint> history = fundamentalHistoryRepository
                .findTop8ByInstrumentIdOrderByPeriodDesc(instrument.getId())
                .stream()
                .map(h -> FundamentalHistoryPoint.builder()
                        .period(h.getPeriod())
                        .revenue(h.getRevenue())
                        .netIncome(h.getNetIncome())
                        .grossMargin(h.getGrossMargin())
                        .netMargin(h.getNetMargin())
                        .roe(h.getRoe())
                        .peRatio(h.getPeRatio())
                        .build())
                .toList();

        return ApiResponse.<List<FundamentalHistoryPoint>>builder()
                .success(true)
                .data(history)
                .message("Temel analiz geçmişi getirildi")
                .build();
    }

    @GetMapping("/fundamental/{instrumentCode}/financials")
    public ApiResponse<List<FinancialDataResponse>> getFinancialData(
            @PathVariable String instrumentCode,
            @RequestParam(defaultValue = "ANNUAL") String periodType) {
        logger.info("Ham finansal veriler getiriliyor: instrument={}, periodType={}", instrumentCode, periodType);

        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        List<FinancialDataResponse> financials = companyFinancialsRepository
                .findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrument.getId(), periodType.toUpperCase())
                .stream()
                .map(f -> FinancialDataResponse.builder()
                        .id(f.getId())
                        .period(f.getPeriod())
                        .periodType(f.getPeriodType())
                        .revenue(f.getRevenue())
                        .grossProfit(f.getGrossProfit())
                        .netIncome(f.getNetIncome())
                        .totalAssets(f.getTotalAssets())
                        .totalEquity(f.getTotalEquity())
                        .totalLiabilities(f.getTotalLiabilities())
                        .currentAssets(f.getCurrentAssets())
                        .currentLiabilities(f.getCurrentLiabilities())
                        .operatingCashFlow(f.getOperatingCashFlow())
                        .build())
                .toList();

        return ApiResponse.<List<FinancialDataResponse>>builder()
                .success(true)
                .data(financials)
                .message("Ham finansal veriler getirildi")
                .build();
    }

    @PostMapping("/fundamental/{instrumentCode}/calculate")
    public ApiResponse<FundamentalRatiosResponse> triggerCalculation(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        if (currentUser.role() != UserRole.ADMIN) {
            throw new PremiumRequiredException("Bu işlem sadece admin kullanıcılara açıktır");
        }
        logger.info("Temel analiz hesaplama tetikleniyor: instrument={}, by={}", instrumentCode, currentUser.keycloakId());

        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        CompanyFinancials latest = companyFinancialsRepository
                .findTopByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrument.getId(), "ANNUAL")
                .orElseThrow(() -> new ResourceNotFoundException("ANNUAL finansal veri bulunamadı: " + instrumentCode));

        FundamentalRatios ratios = fundamentalAnalysisService.calculateRatios(instrument.getId(), latest.getPeriod());

        FundamentalRatiosResponse response = FundamentalRatiosResponse.builder()
                .period(ratios.getPeriod())
                .calculationPrice(ratios.getCalculationPrice())
                .peRatio(ratios.getPeRatio())
                .pbRatio(ratios.getPbRatio())
                .grossMargin(ratios.getGrossMargin())
                .netMargin(ratios.getNetMargin())
                .roe(ratios.getRoe())
                .roa(ratios.getRoa())
                .revenueGrowthYoy(ratios.getRevenueGrowthYoy())
                .netIncomeGrowthYoy(ratios.getNetIncomeGrowthYoy())
                .assetGrowthYoy(ratios.getAssetGrowthYoy())
                .debtToEquity(ratios.getDebtToEquity())
                .currentRatio(ratios.getCurrentRatio())
                .overallSignal(ratios.getOverallSignal())
                .grahamNumber(ratios.getGrahamNumber())
                .piotroskiScore(ratios.getPiotroskiScore())
                .altmanZScore(ratios.getAltmanZScore())
                .premiumRequired(false)
                .calculatedAt(ratios.getCalculatedAt())
                .build();

        return ApiResponse.<FundamentalRatiosResponse>builder()
                .success(true)
                .data(response)
                .message("Temel analiz hesaplaması tamamlandı")
                .build();
    }

    // ── Yardımcı metodlar ─────────────────────────────────────────────────────

}
