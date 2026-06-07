package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.service.MacroDataSyncService;
import com.emrehalli.financeportal.market.service.MacroSyncResult;
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
public class MacroAdminController {

    private static final DateTimeFormatter EVDS_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final MacroDataSyncService macroDataSyncService;

    @PostMapping("/tcmb/cpi/sync")
    public MacroSyncResult syncCpi(
            @RequestParam(defaultValue = "01-10-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncCpiFromTcmb(startDate, resolveEndDate(endDate));
    }

    @PostMapping("/tcmb/ppi/sync")
    public MacroSyncResult syncPpi(
            @RequestParam(defaultValue = "01-10-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncPpiFromTcmb(startDate, resolveEndDate(endDate));
    }

    @PostMapping("/tcmb/policy-rate/sync")
    public MacroSyncResult syncPolicyRate(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncPolicyRateFromTcmb(startDate, resolveEndDate(endDate));
    }

    @PostMapping("/tcmb/labor-market/sync")
    public MacroSyncResult syncLaborMarket(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncLaborMarketFromTcmb(startDate, resolveEndDate(endDate));
    }

    @PostMapping("/tcmb/consumer-confidence/sync")
    public MacroSyncResult syncConsumerConfidence(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(required = false) String endDate) {
        return macroDataSyncService.syncConsumerConfidenceFromTcmb(startDate, resolveEndDate(endDate));
    }

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




