package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.BinanceHistoryFetchAsyncService;
import com.emrehalli.financeportal.market.service.BinanceHistoryFetchStatus;
import com.emrehalli.financeportal.market.service.TcmbSyncAsyncService;
import com.emrehalli.financeportal.market.service.TcmbSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/binance")
@RequiredArgsConstructor
public class BinanceAdminController {

    private final BinanceHistoryFetchAsyncService binanceHistoryFetchAsyncService;
    private final BinanceHistoryFetchStatus binanceHistoryFetchStatus;
    private final TcmbSyncAsyncService tcmbSyncAsyncService;
    private final TcmbSyncStatus tcmbSyncStatus;

    @PostMapping("/history/fetch")
    public ApiResponse<Void> fetchHistory(@RequestParam(defaultValue = "1825") int days) {
        binanceHistoryFetchAsyncService.runFetchAsync(days);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Binance history fetch arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @PostMapping("/tcmb/sync")
    public ApiResponse<Void> syncTcmb() {
        tcmbSyncAsyncService.runSyncAsync();
        return ApiResponse.<Void>builder()
                .success(true)
                .message("TCMB sync arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @GetMapping("/history/fetch/status")
    public AdminJobStatusResponse fetchHistoryStatus() {
        return new AdminJobStatusResponse(
                binanceHistoryFetchStatus.isRunning(),
                binanceHistoryFetchStatus.getProcessed(),
                binanceHistoryFetchStatus.getTotal()
        );
    }

    @GetMapping("/tcmb/sync/status")
    public AdminJobStatusResponse syncTcmbStatus() {
        return new AdminJobStatusResponse(
                tcmbSyncStatus.isRunning(),
                tcmbSyncStatus.getProcessed(),
                tcmbSyncStatus.getTotal()
        );
    }
}
