package com.emrehalli.financeportal.portfolio.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioDetailResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioPerformanceResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioRiskSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioResponseDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import com.emrehalli.financeportal.portfolio.service.PortfolioPerformanceHistoryService;
import com.emrehalli.financeportal.portfolio.service.PortfolioRiskAnalysisService;
import com.emrehalli.financeportal.portfolio.service.PortfolioService;
import com.emrehalli.financeportal.portfolio.service.PortfolioValuationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portföy", description = "Yatırım portföyü CRUD ve analiz işlemleri")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingService portfolioHoldingService;
    private final PortfolioPerformanceHistoryService portfolioPerformanceHistoryService;
    private final PortfolioRiskAnalysisService portfolioRiskAnalysisService;
    private final AppMessageSource appMessageSource;

    public PortfolioController(PortfolioService portfolioService,
                               PortfolioHoldingService portfolioHoldingService,
                               PortfolioPerformanceHistoryService portfolioPerformanceHistoryService,
                               PortfolioRiskAnalysisService portfolioRiskAnalysisService,
                               AppMessageSource appMessageSource) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingService = portfolioHoldingService;
        this.portfolioPerformanceHistoryService = portfolioPerformanceHistoryService;
        this.portfolioRiskAnalysisService = portfolioRiskAnalysisService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Yeni portföy oluştur", description = "Belirtilen kullanıcı için yeni bir yatırım portföyü oluşturur")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy başarıyla oluşturuldu"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz istek verisi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @PostMapping("/{userId}")
    public ApiResponse<PortfolioResponseDto> createPortfolio(@PathVariable Long userId,
                                                             @Valid @RequestBody CreatePortfolioRequest request) {

        PortfolioResponseDto portfolio = portfolioService.createPortfolio(userId, request);

        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(portfolio)
                .message(appMessageSource.get("portfolio.created"))
                .build();
    }

    @Operation(summary = "Kullanıcının portföylerini listele", description = "Belirtilen kullanıcıya ait tüm portföyleri listeler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy listesi başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @GetMapping("/user/{userId}")
    public ApiResponse<List<PortfolioResponseDto>> getUserPortfolios(@PathVariable Long userId) {
        List<PortfolioResponseDto> portfolios = portfolioService.getPortfoliosByUserId(userId);

        return ApiResponse.<List<PortfolioResponseDto>>builder()
                .success(true)
                .data(portfolios)
                .message(appMessageSource.get("portfolio.list.fetched"))
                .build();
    }

    @Operation(summary = "Portföy bilgilerini güncelle", description = "Mevcut bir portföyün bilgilerini günceller")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy başarıyla güncellendi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz istek verisi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @PutMapping("/{portfolioId}")
    public ApiResponse<PortfolioResponseDto> updatePortfolio(@PathVariable Long portfolioId,
                                                             @Valid @RequestBody UpdatePortfolioRequest request) {
        PortfolioResponseDto portfolio = portfolioService.updatePortfolio(portfolioId, request);
        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(portfolio)
                .message(appMessageSource.get("portfolio.updated"))
                .build();
    }

    @Operation(summary = "Portföyü sil", description = "Belirtilen portföyü kalıcı olarak siler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy başarıyla silindi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @DeleteMapping("/{portfolioId}")
    public ApiResponse<Void> deletePortfolio(@PathVariable Long portfolioId) {
        portfolioService.deletePortfolio(portfolioId);
        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("portfolio.deleted"))
                .build();
    }

    @Operation(summary = "Portföy detayını getir", description = "Kimlik numarasına göre tek bir portföyün temel bilgilerini getirir")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @GetMapping("/{portfolioId}")
    public ApiResponse<PortfolioResponseDto> getPortfolioById(@PathVariable Long portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolioEntityById(portfolioId);
        PortfolioResponseDto data = new PortfolioResponseDto(
                portfolio.getId(),
                portfolio.getPortfolioName(),
                portfolio.getCreatedAt(),
                portfolio.getUser() != null ? portfolio.getUser().getId() : null
        );
        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.fetched"))
                .build();
    }

    @Operation(summary = "Portföy finansal özetini getir", description = "Toplam maliyet, güncel değer, günlük ve kümülatif kar/zarar gibi özet göstergeleri döner")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy özeti başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @GetMapping("/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getPortfolioSummary(@PathVariable Long portfolioId) {
        PortfolioSummaryResponse summary = portfolioHoldingService.getPortfolioSummary(portfolioId);

        return ApiResponse.<PortfolioSummaryResponse>builder()
                .success(true)
                .data(summary)
                .message(appMessageSource.get("portfolio.summary.fetched"))
                .build();
    }

    @Operation(summary = "Portföy detaylı görünümünü getir", description = "Temel bilgiler, finansal özet ve tüm varlıkların listesini tek bir yanıt içerisinde döner")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy detayları başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @GetMapping("/{portfolioId}/details")
    public ApiResponse<PortfolioDetailResponse> getPortfolioDetails(@PathVariable Long portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolioEntityById(portfolioId);
        PortfolioValuationResult valuation = portfolioHoldingService.getPortfolioValuation(portfolioId);

        PortfolioDetailResponse response = PortfolioDetailResponse.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getPortfolioName())
                .createdAt(portfolio.getCreatedAt())
                .summary(valuation.summary())
                .holdings(valuation.holdings())
                .build();

        return ApiResponse.<PortfolioDetailResponse>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("portfolio.details.fetched"))
                .build();
    }

    @Operation(summary = "Portföy performans geçmişini getir", description = "Belirli bir tarih aralığındaki performans zaman serisi verilerini döner")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Performans geçmişi başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @GetMapping("/{portfolioId}/performance-history")
    public ApiResponse<PortfolioPerformanceResponse> getPortfolioPerformanceHistory(
            @PathVariable Long portfolioId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        PortfolioPerformanceResponse data = portfolioPerformanceHistoryService.getPerformanceHistory(portfolioId, from, to);
        return ApiResponse.<PortfolioPerformanceResponse>builder()
                .success(true)
                .data(data)
                .message("OK")
                .build();
    }

    @Operation(summary = "Portföy risk analizini getir", description = "Çeşitlendirme, volatilite ve likidite metriklerini içeren risk özetini döner")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Risk analizi başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @GetMapping("/{portfolioId}/risk-summary")
    public ApiResponse<PortfolioRiskSummaryResponse> getPortfolioRiskSummary(@PathVariable Long portfolioId) {
        PortfolioRiskSummaryResponse data = portfolioRiskAnalysisService.getRiskSummary(portfolioId);
        return ApiResponse.<PortfolioRiskSummaryResponse>builder()
                .success(true)
                .data(data)
                .message("OK")
                .build();
    }
}

