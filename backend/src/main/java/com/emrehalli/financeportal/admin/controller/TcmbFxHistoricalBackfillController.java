package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.admin.dto.AdminJobStatusResponse;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.TcmbFxHistoryBackfillAsyncService;
import com.emrehalli.financeportal.market.service.TcmbFxHistoryBackfillStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/markets/fx/tcmb/history")
@RequiredArgsConstructor
public class TcmbFxHistoricalBackfillController {

    private final TcmbFxHistoryBackfillAsyncService tcmbFxHistoryBackfillAsyncService;
    private final TcmbFxHistoryBackfillStatus tcmbFxHistoryBackfillStatus;

    @PostMapping("/backfill")
    public ApiResponse<Void> backfill() {
        tcmbFxHistoryBackfillAsyncService.runBackfillAsync();
        return ApiResponse.<Void>builder()
                .success(true)
                .message("TCMB FX historical backfill arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @GetMapping("/backfill/status")
    public AdminJobStatusResponse backfillStatus() {
        return new AdminJobStatusResponse(
                tcmbFxHistoryBackfillStatus.isRunning(),
                tcmbFxHistoryBackfillStatus.getProcessed(),
                tcmbFxHistoryBackfillStatus.getTotal()
        );
    }
}



