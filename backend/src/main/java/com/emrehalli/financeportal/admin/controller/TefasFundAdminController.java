package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.provider.fund.TefasProvider;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundListResponseItem;
import com.emrehalli.financeportal.market.service.FundFetchAsyncService;
import com.emrehalli.financeportal.market.service.FundFetchStatus;
import com.emrehalli.financeportal.market.service.TefasFundBackfillAsyncService;
import com.emrehalli.financeportal.market.service.TefasFundBackfillStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/funds")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Fon Yonetimi", description = "TEFAS fon veri senkronizasyon yonetimi")
public class TefasFundAdminController {

    private final TefasFundBackfillAsyncService tefasFundBackfillAsyncService;
    private final TefasFundBackfillStatus tefasFundBackfillStatus;
    private final TefasProvider tefasProvider;
    private final FundFetchAsyncService fundFetchAsyncService;
    private final FundFetchStatus fundFetchStatus;

    @Operation(summary = "Fon gecmis verisi doldur", description = "TEFAS fon verilerinin asenkron tarihsel backfill islemini baslatir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Backfill islemi kabul edildi"))
    @PostMapping("/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> backfill(@RequestBody(required = false) TefasFundBackfillRequest request) {
        if (tefasFundBackfillStatus.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Backfill zaten calisiyor")
                            .data(null)
                            .build());
        }

        TefasFundBackfillRequest effectiveRequest =
                request != null ? request : new TefasFundBackfillRequest();
        tefasFundBackfillAsyncService.runAsync(effectiveRequest.getFundCode(), effectiveRequest.getPeriyod());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<Void>builder()
                        .success(true)
                        .message("Backfill baslatildi")
                        .data(null)
                        .build());
    }

    @Operation(summary = "Fon backfill durumunu sorgula", description = "TEFAS fon backfill isleminin detayli ilerleme durumunu raporlar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Durum bilgisi basariyla getirildi"))
    @GetMapping("/backfill/status")
    @PreAuthorize("hasRole('ADMIN')")
    public TefasFundBackfillStatusResponse backfillStatus() {
        return new TefasFundBackfillStatusResponse(
                tefasFundBackfillStatus.isRunning(),
                tefasFundBackfillStatus.getProcessedFunds(),
                tefasFundBackfillStatus.getSavedRecords(),
                tefasFundBackfillStatus.getSkippedDuplicates(),
                tefasFundBackfillStatus.getStartedAt(),
                tefasFundBackfillStatus.getFinishedAt(),
                tefasFundBackfillStatus.getLastError()
        );
    }

    @Operation(summary = "TEFAS baglanti testi", description = "TEFAS saglayicisina canli baglanti kontrolu yapar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Baglanti testi sonucu donduruldu"))
    @GetMapping("/test-connection")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> testConnection() {
        try {
            java.util.List<TefasFundListResponseItem> funds = tefasProvider.fetchAllFunds();
            TefasFundListResponseItem firstFund = funds.isEmpty() ? null : funds.getFirst();
            TefasTestConnectionResponse response = new TefasTestConnectionResponse(
                    "ok",
                    funds.size(),
                    firstFund == null ? null : new TefasFundSummary(firstFund.getFonKodu(), firstFund.getFonUnvan()),
                    null
            );
            log.info("TEFAS test connection result: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            TefasTestConnectionResponse response = new TefasTestConnectionResponse(
                    "error",
                    0,
                    null,
                    exception.getMessage()
            );
            log.info("TEFAS test connection result: {}", response);
            return ResponseEntity.ok(response);
        }
    }

    @Operation(summary = "Guncel fon verilerini cek", description = "TEFAS fon anlık goruntusunun asenkron cekilmesini baslatir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Fon cekme islemi kabul edildi"))
    @PostMapping("/fetch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> fetchFunds() {
        if (fundFetchStatus.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Fetch zaten calisiyor")
                            .data(null)
                            .build());
        }

        fundFetchAsyncService.runAsync();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Islem baslatildi")
                        .data(null)
                        .build()
        );
    }

    @Operation(summary = "Fon cekme durumunu sorgula", description = "TEFAS fon cekme isleminin detayli ilerleme durumunu raporlar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Durum bilgisi basariyla getirildi"))
    @GetMapping("/fetch/status")
    @PreAuthorize("hasRole('ADMIN')")
    public FundFetchStatusResponse fetchStatus() {
        return new FundFetchStatusResponse(
                fundFetchStatus.isRunning(),
                fundFetchStatus.getProcessedFunds(),
                fundFetchStatus.getSavedFunds(),
                fundFetchStatus.getStartedAt(),
                fundFetchStatus.getFinishedAt(),
                fundFetchStatus.getLastError()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TefasFundBackfillRequest {
        private String fundCode;
        private Integer periyod;
    }

    public record TefasFundBackfillStatusResponse(
            boolean running,
            int processedFunds,
            int savedRecords,
            int skippedDuplicates,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String lastError
    ) {
    }

    public record TefasTestConnectionResponse(
            String status,
            int fundCount,
            TefasFundSummary firstFund,
            String message
    ) {
    }

    public record TefasFundSummary(
            String fonKodu,
            String fonUnvan
    ) {
    }

    public record FundFetchStatusResponse(
            boolean running,
            int processedFunds,
            int savedFunds,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String lastError
    ) {
    }
}

