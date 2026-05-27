package com.emrehalli.financeportal.analysis.controller;

import com.emrehalli.financeportal.analysis.dto.*;
import com.emrehalli.financeportal.analysis.entity.CompanyFinancials;
import com.emrehalli.financeportal.analysis.entity.FundamentalHistory;
import com.emrehalli.financeportal.analysis.entity.FundamentalRatios;
import com.emrehalli.financeportal.analysis.entity.IndicatorConfig;
import com.emrehalli.financeportal.analysis.exception.PremiumRequiredException;
import com.emrehalli.financeportal.analysis.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.analysis.repository.FundamentalHistoryRepository;
import com.emrehalli.financeportal.analysis.repository.FundamentalRatiosRepository;
import com.emrehalli.financeportal.analysis.repository.IndicatorConfigRepository;
import com.emrehalli.financeportal.analysis.service.ChartDrawingService;
import com.emrehalli.financeportal.analysis.service.FundamentalAnalysisService;
import com.emrehalli.financeportal.analysis.service.TechnicalIndicatorService;
import com.emrehalli.financeportal.analysis.service.TechnicalIndicatorService.BollingerResult;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.config.security.RequiresPremium;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Logger logger = LogManager.getLogger(AnalysisController.class);

    private final TechnicalIndicatorService technicalIndicatorService;
    private final ChartDrawingService chartDrawingService;
    private final FundamentalAnalysisService fundamentalAnalysisService;
    private final FundamentalRatiosRepository fundamentalRatiosRepository;
    private final FundamentalHistoryRepository fundamentalHistoryRepository;
    private final CompanyFinancialsRepository companyFinancialsRepository;
    private final IndicatorConfigRepository indicatorConfigRepository;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;

    public AnalysisController(TechnicalIndicatorService technicalIndicatorService,
                              ChartDrawingService chartDrawingService,
                              FundamentalAnalysisService fundamentalAnalysisService,
                              FundamentalRatiosRepository fundamentalRatiosRepository,
                              FundamentalHistoryRepository fundamentalHistoryRepository,
                              CompanyFinancialsRepository companyFinancialsRepository,
                              IndicatorConfigRepository indicatorConfigRepository,
                              MarketInstrumentRepository marketInstrumentRepository,
                              MarketPriceHistoryRepository marketPriceHistoryRepository,
                              UserRepository userRepository,
                              CurrentUserResolver currentUserResolver) {
        this.technicalIndicatorService = technicalIndicatorService;
        this.chartDrawingService = chartDrawingService;
        this.fundamentalAnalysisService = fundamentalAnalysisService;
        this.fundamentalRatiosRepository = fundamentalRatiosRepository;
        this.fundamentalHistoryRepository = fundamentalHistoryRepository;
        this.companyFinancialsRepository = companyFinancialsRepository;
        this.indicatorConfigRepository = indicatorConfigRepository;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketPriceHistoryRepository = marketPriceHistoryRepository;
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
    }

    // ── Teknik Analiz ─────────────────────────────────────────────────────────

    @GetMapping("/technical/{instrumentCode}")
    public ApiResponse<TechnicalFullResponse> getTechnicalData(
            @PathVariable String instrumentCode,
            @RequestParam(defaultValue = "1d") String timeframe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        logger.info("Teknik analiz isteği: instrument={}, timeframe={}, from={}, to={}", instrumentCode, timeframe, from, to);

        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        // DB'de sadece ONE_DAY verisi var — tüm timeframe'ler bu interval üzerinden sorgulanır
        IntervalType intervalType = IntervalType.ONE_DAY;
        LocalDate effectiveFrom = from != null ? from : getStartDate(timeframe);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();

        Instant fromInstant = effectiveFrom.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = effectiveTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<MarketPriceHistory> history = marketPriceHistoryRepository
                .findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrument, intervalType, fromInstant, toInstant);

        List<OhlcvPoint> prices = history.stream()
                .map(h -> OhlcvPoint.builder()
                        .time(h.getPriceTimestamp().getEpochSecond())
                        .value(safeDouble(h.getClosePrice()))
                        .build())
                .toList();

        // GUEST için sadece fiyat verisi döner
        Map<String, Object> indicators = new HashMap<>();
        try {
            CurrentUser currentUser = currentUserResolver.resolve();
            if (currentUser.role() != UserRole.GUEST) {
                indicators = buildIndicators(history);
            }
        } catch (Exception e) {
            // Authenticated olmayan kullanıcı — sadece fiyat verisi
        }

        TechnicalFullResponse response = TechnicalFullResponse.builder()
                .symbol(instrumentCode.toUpperCase())
                .timeframe(timeframe)
                .prices(prices)
                .indicators(indicators.isEmpty() ? null : indicators)
                .build();

        logger.info("Teknik analiz tamamlandı: instrument={}, veriNoktası={}", instrumentCode, prices.size());
        return ApiResponse.<TechnicalFullResponse>builder()
                .success(true)
                .data(response)
                .message("Teknik analiz verisi başarıyla getirildi")
                .build();
    }

    @GetMapping("/technical/{instrumentCode}/indicators")
    public ApiResponse<List<IndicatorConfigResponse>> getUserIndicators(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gösterge konfigürasyonları getiriliyor: keycloakId={}, instrument={}", currentUser.keycloakId(), instrumentCode);

        User user = resolveUser(currentUser.keycloakId());
        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        List<IndicatorConfigResponse> configs = indicatorConfigRepository
                .findByUserIdAndInstrumentIdAndIsActiveTrue(user.getId(), instrument.getId())
                .stream()
                .map(this::toIndicatorResponse)
                .toList();

        return ApiResponse.<List<IndicatorConfigResponse>>builder()
                .success(true)
                .data(configs)
                .message("Gösterge konfigürasyonları getirildi")
                .build();
    }

    @PostMapping("/technical/{instrumentCode}/indicators")
    public ApiResponse<IndicatorConfigResponse> saveIndicatorConfig(
            @PathVariable String instrumentCode,
            @Valid @RequestBody IndicatorConfigRequest req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gösterge konfigürasyonu kaydediliyor: keycloakId={}, instrument={}, type={}",
                currentUser.keycloakId(), instrumentCode, req.getIndicatorType());

        User user = resolveUser(currentUser.keycloakId());
        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

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
                .message("Gösterge konfigürasyonu kaydedildi")
                .build();
    }

    @DeleteMapping("/technical/indicators/{id}")
    public ApiResponse<Void> deleteIndicatorConfig(@PathVariable Long id) {
        CurrentUser currentUser = currentUserResolver.resolve();
        User user = resolveUser(currentUser.keycloakId());
        logger.info("Gösterge konfigürasyonu siliniyor: keycloakId={}, configId={}", currentUser.keycloakId(), id);

        indicatorConfigRepository.deleteByIdAndUserId(id, user.getId());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Gösterge konfigürasyonu silindi")
                .build();
    }

    // ── Çizimler ─────────────────────────────────────────────────────────────

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
    public ApiResponse<List<FinancialDataResponse>> getFinancialData(@PathVariable String instrumentCode) {
        logger.info("Ham finansal veriler getiriliyor: instrument={}", instrumentCode);

        MarketInstrument instrument = marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));

        List<FinancialDataResponse> financials = companyFinancialsRepository
                .findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrument.getId(), "ANNUAL")
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

    // ── Yardımcı metodlar ─────────────────────────────────────────────────────

    private Map<String, Object> buildIndicators(List<MarketPriceHistory> history) {
        if (history.isEmpty()) return new HashMap<>();

        List<Double> closes = history.stream()
                .map(h -> h.getClosePrice() != null ? h.getClosePrice().doubleValue() : 0.0)
                .toList();

        Map<String, Object> result = new HashMap<>();

        try {
            if (closes.size() >= 20) {
                result.put("ma20", technicalIndicatorService.calculateSMA(closes, 20));
            }
            if (closes.size() >= 50) {
                result.put("ma50", technicalIndicatorService.calculateSMA(closes, 50));
            }
            if (closes.size() >= 20) {
                result.put("ema20", technicalIndicatorService.calculateEMA(closes, 20));
            }
            if (closes.size() >= 20) {
                BollingerResult bb = technicalIndicatorService.calculateBollingerBands(closes, 20, 2.0);
                result.put("bollinger_upper", bb.upper());
                result.put("bollinger_middle", bb.middle());
                result.put("bollinger_lower", bb.lower());
            }
        } catch (Exception e) {
            logger.error("Gösterge hesaplama hatası: {}", e.getMessage(), e);
        }

        return result;
    }

    private LocalDate getStartDate(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "1h"  -> LocalDate.now().minusMonths(3);
            case "4h"  -> LocalDate.now().minusMonths(6);
            case "1d"  -> LocalDate.now().minusYears(2);
            case "1w"  -> LocalDate.now().minusYears(5);
            default    -> LocalDate.now().minusYears(1);
        };
    }

    private double safeDouble(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: keycloakId=" + keycloakId));
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
