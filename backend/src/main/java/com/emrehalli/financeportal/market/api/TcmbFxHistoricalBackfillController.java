package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.TcmbFxHistoricalBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/markets/fx/tcmb/history")
@RequiredArgsConstructor
public class TcmbFxHistoricalBackfillController {

    private final TcmbFxHistoricalBackfillService tcmbFxHistoricalBackfillService;

    @PostMapping("/backfill")
    public ApiResponse<Void> backfill() {
        // This endpoint is temporarily public for manual operations.
        // It should be protected with ADMIN role in a later hardening pass.
        tcmbFxHistoricalBackfillService.backfillAll();

        return ApiResponse.<Void>builder()
                .success(true)
                .message("TCMB FX historical backfill completed")
                .build();
    }
}
