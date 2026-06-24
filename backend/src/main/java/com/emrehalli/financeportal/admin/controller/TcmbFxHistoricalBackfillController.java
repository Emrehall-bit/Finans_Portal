package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.admin.dto.AdminJobStatusResponse;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.TcmbFxHistoryBackfillAsyncService;
import com.emrehalli.financeportal.market.service.TcmbFxHistoryBackfillStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/markets/fx/tcmb/history")
@RequiredArgsConstructor
@Tag(name = "Admin - TCMB Doviz Gecmisi", description = "TCMB doviz kuru gecmis verisi doldurma")
public class TcmbFxHistoricalBackfillController {

    private final TcmbFxHistoryBackfillAsyncService tcmbFxHistoryBackfillAsyncService;
    private final TcmbFxHistoryBackfillStatus tcmbFxHistoryBackfillStatus;

    @Operation(summary = "TCMB doviz gecmisi doldur", description = "TCMB tarihsel doviz kuru verilerinin asenkron backfill islemini baslatir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Backfill islemi baslatildi"))
    @PostMapping("/backfill")
    public ApiResponse<Void> backfill() {
        tcmbFxHistoryBackfillAsyncService.runBackfillAsync();
        return ApiResponse.<Void>builder()
                .success(true)
                .message("TCMB FX historical backfill arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @Operation(summary = "TCMB backfill durumunu sorgula", description = "TCMB doviz gecmisi doldurma isleminin anlik ilerleme durumunu raporlar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Durum bilgisi basariyla getirildi"))
    @GetMapping("/backfill/status")
    public AdminJobStatusResponse backfillStatus() {
        return new AdminJobStatusResponse(
                tcmbFxHistoryBackfillStatus.isRunning(),
                tcmbFxHistoryBackfillStatus.getProcessed(),
                tcmbFxHistoryBackfillStatus.getTotal()
        );
    }
}

