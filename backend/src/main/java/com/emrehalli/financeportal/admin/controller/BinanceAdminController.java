package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.admin.dto.AdminJobStatusResponse;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.BinanceHistoryFetchAsyncService;
import com.emrehalli.financeportal.market.service.BinanceHistoryFetchStatus;
import com.emrehalli.financeportal.market.service.TcmbSyncAsyncService;
import com.emrehalli.financeportal.market.service.TcmbSyncStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/binance")
@RequiredArgsConstructor
@Tag(name = "Admin - Binance", description = "Binance kripto veri senkronizasyon yonetimi")
public class BinanceAdminController {

    private final BinanceHistoryFetchAsyncService binanceHistoryFetchAsyncService;
    private final BinanceHistoryFetchStatus binanceHistoryFetchStatus;
    private final TcmbSyncAsyncService tcmbSyncAsyncService;
    private final TcmbSyncStatus tcmbSyncStatus;

    @Operation(summary = "Binance gecmis verisi cek", description = "Belirtilen gun sayisi kadar Binance fiyat gecmisini asenkron olarak cekmevi baslatir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gecmis veri cekme islemi baslatildi"))
    @PostMapping("/history/fetch")
    public ApiResponse<Void> fetchHistory(@RequestParam(defaultValue = "1825") int days) {
        binanceHistoryFetchAsyncService.runFetchAsync(days);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Binance history fetch arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @Operation(summary = "TCMB verilerini senkronize et", description = "TCMB referans verilerinin asenkron senkronizasyonunu baslatir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "TCMB senkronizasyonu baslatildi"))
    @PostMapping("/tcmb/sync")
    public ApiResponse<Void> syncTcmb() {
        tcmbSyncAsyncService.runSyncAsync();
        return ApiResponse.<Void>builder()
                .success(true)
                .message("TCMB sync arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @Operation(summary = "Binance gecmis cekme durumunu sorgula", description = "Binance gecmis veri cekme isleminin anlik ilerleme durumunu raporlar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Durum bilgisi basariyla getirildi"))
    @GetMapping("/history/fetch/status")
    public AdminJobStatusResponse fetchHistoryStatus() {
        return new AdminJobStatusResponse(
                binanceHistoryFetchStatus.isRunning(),
                binanceHistoryFetchStatus.getProcessed(),
                binanceHistoryFetchStatus.getTotal()
        );
    }

    @Operation(summary = "TCMB senkronizasyon durumunu sorgula", description = "TCMB senkronizasyon isleminin anlik ilerleme durumunu raporlar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Durum bilgisi basariyla getirildi"))
    @GetMapping("/tcmb/sync/status")
    public AdminJobStatusResponse syncTcmbStatus() {
        return new AdminJobStatusResponse(
                tcmbSyncStatus.isRunning(),
                tcmbSyncStatus.getProcessed(),
                tcmbSyncStatus.getTotal()
        );
    }
}

