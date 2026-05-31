package com.emrehalli.financeportal.technicalanalysis.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.config.security.RequiresPremium;
import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingDtos.LinkAlertRequest;
import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingDtos.Request;
import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingDtos.Response;
import com.emrehalli.financeportal.technicalanalysis.drawing.service.ChartDrawingService;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FinancialDataResponse;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FundamentalHistoryPoint;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FundamentalRatiosResponse;
import com.emrehalli.financeportal.technicalanalysis.fundamental.service.FundamentalAccessPolicy;
import com.emrehalli.financeportal.technicalanalysis.fundamental.service.FundamentalAnalysisService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Logger logger = LogManager.getLogger(AnalysisController.class);

    private final ChartDrawingService chartDrawingService;
    private final FundamentalAnalysisService fundamentalAnalysisService;
    private final FundamentalAccessPolicy fundamentalAccessPolicy;
    private final CurrentUserResolver currentUserResolver;

    public AnalysisController(ChartDrawingService chartDrawingService,
                              FundamentalAnalysisService fundamentalAnalysisService,
                              FundamentalAccessPolicy fundamentalAccessPolicy,
                              CurrentUserResolver currentUserResolver) {
        this.chartDrawingService = chartDrawingService;
        this.fundamentalAnalysisService = fundamentalAnalysisService;
        this.fundamentalAccessPolicy = fundamentalAccessPolicy;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/drawings/{instrumentCode}")
    public ApiResponse<List<Response>> getDrawings(
            @PathVariable String instrumentCode,
            @RequestParam(defaultValue = "1d") String timeframe) {
        CurrentUser currentUser = currentUserResolver.resolve();
        List<Response> drawings = chartDrawingService.getDrawings(currentUser.keycloakId(), instrumentCode, timeframe);
        return ApiResponse.<List<Response>>builder()
                .success(true)
                .data(drawings)
                .message("Cizimler getirildi")
                .build();
    }

    @PostMapping("/drawings/{instrumentCode}")
    public ApiResponse<Response> saveDrawing(
            @PathVariable String instrumentCode,
            @Valid @RequestBody Request req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        Response drawing = chartDrawingService.saveDrawing(
                currentUser.keycloakId(), currentUser.role(), instrumentCode, req);

        return ApiResponse.<Response>builder()
                .success(true)
                .data(drawing)
                .message("Cizim kaydedildi")
                .build();
    }

    @PatchMapping("/drawings/{id}")
    public ApiResponse<Response> updateDrawing(
            @PathVariable Long id,
            @Valid @RequestBody Request req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        Response drawing = chartDrawingService.updateDrawing(currentUser.keycloakId(), id, req);

        return ApiResponse.<Response>builder()
                .success(true)
                .data(drawing)
                .message("Cizim guncellendi")
                .build();
    }

    @DeleteMapping("/drawings/{id}")
    public ApiResponse<Void> deleteDrawing(@PathVariable Long id) {
        CurrentUser currentUser = currentUserResolver.resolve();
        chartDrawingService.deleteDrawing(currentUser.keycloakId(), id);

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Cizim silindi")
                .build();
    }

    @PostMapping("/drawings/{id}/link-alert")
    @RequiresPremium
    public ApiResponse<Response> linkDrawingToAlert(
            @PathVariable Long id,
            @Valid @RequestBody LinkAlertRequest req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Cizim alert'e baglaniyor: keycloakId={}, drawingId={}, alertId={}",
                currentUser.keycloakId(), id, req.getAlertId());

        Response drawing = chartDrawingService.linkDrawingToAlert(
                currentUser.keycloakId(), id, req.getAlertId());

        return ApiResponse.<Response>builder()
                .success(true)
                .data(drawing)
                .message("Cizim alert'e baglandi")
                .build();
    }

    @GetMapping("/fundamental/{instrumentCode}")
    public ApiResponse<FundamentalRatiosResponse> getFundamentalRatios(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Temel analiz oranlari getiriliyor: instrument={}, role={}", instrumentCode, currentUser.role());

        boolean isPremium = fundamentalAccessPolicy.canAccessPremiumContent(currentUser.role());
        FundamentalRatiosResponse response = fundamentalAnalysisService.getLatestRatios(instrumentCode, isPremium);

        return ApiResponse.<FundamentalRatiosResponse>builder()
                .success(true)
                .data(response)
                .message("Temel analiz oranlari getirildi")
                .build();
    }

    @GetMapping("/fundamental/{instrumentCode}/history")
    @RequiresPremium
    public ApiResponse<List<FundamentalHistoryPoint>> getFundamentalHistory(@PathVariable String instrumentCode) {
        logger.info("Temel analiz gecmisi getiriliyor: instrument={}", instrumentCode);

        List<FundamentalHistoryPoint> history = fundamentalAnalysisService.getHistory(instrumentCode);

        return ApiResponse.<List<FundamentalHistoryPoint>>builder()
                .success(true)
                .data(history)
                .message("Temel analiz gecmisi getirildi")
                .build();
    }

    @GetMapping("/fundamental/{instrumentCode}/financials")
    public ApiResponse<List<FinancialDataResponse>> getFinancialData(
            @PathVariable String instrumentCode,
            @RequestParam(defaultValue = "ANNUAL") String periodType) {
        logger.info("Ham finansal veriler getiriliyor: instrument={}, periodType={}", instrumentCode, periodType);

        List<FinancialDataResponse> financials = fundamentalAnalysisService.getFinancialData(instrumentCode, periodType);

        return ApiResponse.<List<FinancialDataResponse>>builder()
                .success(true)
                .data(financials)
                .message("Ham finansal veriler getirildi")
                .build();
    }

    @PostMapping("/fundamental/{instrumentCode}/calculate")
    public ApiResponse<FundamentalRatiosResponse> triggerCalculation(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        fundamentalAccessPolicy.requireAdmin(currentUser.role());
        logger.info("Temel analiz hesaplama tetikleniyor: instrument={}, by={}", instrumentCode, currentUser.keycloakId());

        FundamentalRatiosResponse response = fundamentalAnalysisService.calculateLatestAnnualRatios(instrumentCode);

        return ApiResponse.<FundamentalRatiosResponse>builder()
                .success(true)
                .data(response)
                .message("Temel analiz hesaplamasi tamamlandi")
                .build();
    }
}
