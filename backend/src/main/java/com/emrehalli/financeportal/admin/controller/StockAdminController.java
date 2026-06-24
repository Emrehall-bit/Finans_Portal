package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.admin.dto.AdminJobStatusResponse;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.StockFetchAsyncService;
import com.emrehalli.financeportal.market.service.StockFetchStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stocks")
@RequiredArgsConstructor
@Tag(name = "Admin - Hisse Yonetimi", description = "Hisse senedi veri senkronizasyon yonetimi")
public class StockAdminController {

    private final StockFetchAsyncService stockFetchAsyncService;
    private final StockFetchStatus stockFetchStatus;

    @Operation(summary = "Hisse fiyatlarini cek", description = "BIST hisse senedi fiyatlarinin asenkron olarak cekilmesini baslatir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hisse fiyat cekme islemi baslatildi"))
    @PostMapping("/fetch")
    public ApiResponse<Void> fetch() {
        stockFetchAsyncService.runFetchAsync();
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Stock fetch arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @Operation(summary = "Hisse cekme durumunu sorgula", description = "Hisse senedi fiyat cekme isleminin anlik ilerleme durumunu raporlar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Durum bilgisi basariyla getirildi"))
    @GetMapping("/fetch/status")
    public AdminJobStatusResponse fetchStatus() {
        return new AdminJobStatusResponse(
                stockFetchStatus.isRunning(),
                stockFetchStatus.getProcessed(),
                stockFetchStatus.getTotal()
        );
    }
}

