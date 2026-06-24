package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.service.MacroDataSyncService;
import com.emrehalli.financeportal.market.service.MacroSyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/admin/markets/macro")
@RequiredArgsConstructor
@Tag(name = "Admin - Makro Veri Yonetimi", description = "TCMB makroekonomik veri senkronizasyon islemleri")
public class MacroAdminController {

    private static final DateTimeFormatter EVDS_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final MacroDataSyncService macroDataSyncService;

    @Operation(summary = "TUFE verilerini senkronize et", description = "TCMB EVDS uzerinden tuketici fiyat endeksi verilerini ceker")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "TUFE verileri basariyla senkronize edildi"))
    @PostMapping("/tcmb/cpi/sync")
    public MacroSyncResult syncCpi(
            @RequestParam(defaultValue = "01-10-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncCpiFromTcmb(startDate, resolveEndDate(endDate));
    }

    @Operation(summary = "UFE verilerini senkronize et", description = "TCMB EVDS uzerinden uretici fiyat endeksi verilerini ceker")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "UFE verileri basariyla senkronize edildi"))
    @PostMapping("/tcmb/ppi/sync")
    public MacroSyncResult syncPpi(
            @RequestParam(defaultValue = "01-10-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncPpiFromTcmb(startDate, resolveEndDate(endDate));
    }

    @Operation(summary = "Politika faizini senkronize et", description = "TCMB EVDS uzerinden politika faiz orani verilerini ceker")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Politika faizi verileri basariyla senkronize edildi"))
    @PostMapping("/tcmb/policy-rate/sync")
    public MacroSyncResult syncPolicyRate(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncPolicyRateFromTcmb(startDate, resolveEndDate(endDate));
    }

    @Operation(summary = "Isgucu piyasasi verilerini senkronize et", description = "TCMB EVDS uzerinden isgucu piyasasi verilerini ceker")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Isgucu piyasasi verileri basariyla senkronize edildi"))
    @PostMapping("/tcmb/labor-market/sync")
    public MacroSyncResult syncLaborMarket(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncLaborMarketFromTcmb(startDate, resolveEndDate(endDate));
    }

    @Operation(summary = "Tuketici guveni verilerini senkronize et", description = "TCMB EVDS uzerinden tuketici guven endeksi verilerini ceker")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tuketici guveni verileri basariyla senkronize edildi"))
    @PostMapping("/tcmb/consumer-confidence/sync")
    public MacroSyncResult syncConsumerConfidence(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncConsumerConfidenceFromTcmb(startDate, resolveEndDate(endDate));
    }

    @Operation(summary = "Cari islemler verilerini senkronize et", description = "TCMB EVDS uzerinden cari islemler dengesi verilerini ceker")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cari islemler verileri basariyla senkronize edildi"))
    @PostMapping("/tcmb/current-account/sync")
    public MacroSyncResult syncCurrentAccount(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncCurrentAccountFromTcmb(startDate, resolveEndDate(endDate));
    }

    private String resolveEndDate(String endDate) {
        if (endDate != null && !endDate.isBlank()) {
            return endDate;
        }
        return LocalDate.now().format(EVDS_DATE_FORMATTER);
    }
}

