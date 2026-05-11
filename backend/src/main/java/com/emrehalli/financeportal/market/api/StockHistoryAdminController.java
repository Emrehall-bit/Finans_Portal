package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.BackfillStatus;
import com.emrehalli.financeportal.market.service.StockHistoryBackfillAsyncService;
import com.emrehalli.financeportal.market.support.BistSymbolRegistry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/stocks/history")
@AllArgsConstructor
public class StockHistoryAdminController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final StockHistoryBackfillAsyncService stockHistoryBackfillAsyncService;
    private final BackfillStatus backfillStatus;
    private final BistSymbolRegistry bistSymbolRegistry;

    @PostMapping("/backfill")
    public ApiResponse<Void> backfill(@RequestBody(required = false) StockHistoryBackfillRequest request) {
        StockHistoryBackfillRequest effectiveRequest = request != null ? request : new StockHistoryBackfillRequest();
        LocalDate endDate = parseDateOrDefault(effectiveRequest.getEndDate(), LocalDate.now());
        LocalDate startDate = parseDateOrDefault(effectiveRequest.getStartDate(), endDate.minusYears(10));
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("endDate cannot be before startDate");
        }

        List<String> symbols = resolveSymbols(effectiveRequest.getSymbol());
        stockHistoryBackfillAsyncService.runBackfillAsync(symbols, startDate, endDate);

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Backfill arka planda baslatildi. Loglari takip edin.")
                .data(null)
                .dataDate(LocalDateTime.now())
                .build();
    }

    @GetMapping("/backfill/status")
    public AdminJobStatusResponse backfillStatus() {
        return new AdminJobStatusResponse(
                backfillStatus.isRunning(),
                backfillStatus.getProcessed(),
                backfillStatus.getTotal()
        );
    }

    private List<String> resolveSymbols(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return bistSymbolRegistry.getAllSymbols();
        }
        return List.of(symbol.trim().toUpperCase());
    }

    private LocalDate parseDateOrDefault(String value, LocalDate defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return LocalDate.parse(value, DATE_FORMATTER);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockHistoryBackfillRequest {
        private String symbol;
        private String startDate;
        private String endDate;
    }

}
