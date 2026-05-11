package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.StockFetchAsyncService;
import com.emrehalli.financeportal.market.service.StockFetchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stocks")
@RequiredArgsConstructor
public class StockAdminController {

    private final StockFetchAsyncService stockFetchAsyncService;
    private final StockFetchStatus stockFetchStatus;

    @PostMapping("/fetch")
    public ApiResponse<Void> fetch() {
        stockFetchAsyncService.runFetchAsync();
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Stock fetch arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .build();
    }

    @GetMapping("/fetch/status")
    public AdminJobStatusResponse fetchStatus() {
        return new AdminJobStatusResponse(
                stockFetchStatus.isRunning(),
                stockFetchStatus.getProcessed(),
                stockFetchStatus.getTotal()
        );
    }
}
