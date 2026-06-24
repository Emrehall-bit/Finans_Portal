package com.emrehalli.financeportal.portfolio.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio-holdings")
@Tag(name = "Portföy Varlıkları", description = "Portföy içindeki varlık (holding) yönetimi")
public class PortfolioHoldingController {

    private final PortfolioHoldingService holdingService;
    private final AppMessageSource appMessageSource;

    public PortfolioHoldingController(PortfolioHoldingService holdingService, AppMessageSource appMessageSource) {
        this.holdingService = holdingService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Portföye yeni varlık ekle", description = "Belirtilen portföye yeni bir varlık (holding) ekler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Varlık başarıyla eklendi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz istek verisi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @PostMapping("/{portfolioId}")
    public ApiResponse<PortfolioHoldingDto> createHolding(@PathVariable Long portfolioId,
                                                          @Valid @RequestBody CreatePortfolioHoldingRequest request) {
        PortfolioHoldingDto data = holdingService.createHolding(portfolioId, request);
        return ApiResponse.<PortfolioHoldingDto>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.holding.created"))
                .build();
    }

    @Operation(summary = "Varlık bilgilerini güncelle", description = "Belirtilen portföydeki bir varlığın bilgilerini günceller")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Varlık başarıyla güncellendi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz istek verisi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Varlık veya portföy bulunamadı")
    })
    @PutMapping("/{portfolioId}/{holdingId}")
    public ApiResponse<PortfolioHoldingDto> updateHolding(@PathVariable Long portfolioId,
                                                          @PathVariable Long holdingId,
                                                          @Valid @RequestBody UpdatePortfolioHoldingRequest request) {
        PortfolioHoldingDto data = holdingService.updateHolding(portfolioId, holdingId, request);
        return ApiResponse.<PortfolioHoldingDto>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.holding.updated"))
                .build();
    }

    @Operation(summary = "Varlığı portföyden çıkar", description = "Belirtilen varlığı portföyden kalıcı olarak siler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Varlık başarıyla silindi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Varlık veya portföy bulunamadı")
    })
    @DeleteMapping("/{portfolioId}/{holdingId}")
    public ApiResponse<Void> deleteHolding(@PathVariable Long portfolioId,
                                           @PathVariable Long holdingId) {
        holdingService.deleteHolding(portfolioId, holdingId);
        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("portfolio.holding.deleted"))
                .build();
    }

    @Operation(summary = "Portföydeki varlıkları listele", description = "Belirtilen portföydeki tüm varlıkları listeler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Varlık listesi başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @GetMapping("/portfolio/{portfolioId}")
    public ApiResponse<List<PortfolioHoldingDto>> getHoldings(@PathVariable Long portfolioId) {
        List<PortfolioHoldingDto> data = holdingService.getHoldingsByPortfolioId(portfolioId);
        return ApiResponse.<List<PortfolioHoldingDto>>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.holding.list.fetched"))
                .build();
    }

    @Operation(summary = "Portföy özetini getir", description = "Belirtilen portföyün finansal özetini döner")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy özeti başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Portföy bulunamadı")
    })
    @GetMapping("/portfolio/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(@PathVariable Long portfolioId) {
        PortfolioSummaryResponse data = holdingService.getPortfolioSummary(portfolioId);
        return ApiResponse.<PortfolioSummaryResponse>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.summary.fetched"))
                .build();
    }
}

