package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.service.MacroDataSyncService;
import com.emrehalli.financeportal.market.service.MacroSyncResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/markets/macro")
@RequiredArgsConstructor
public class MacroAdminController {

    private final MacroDataSyncService macroDataSyncService;

    @PostMapping("/tcmb/cpi/sync")
    public MacroSyncResult syncCpi(
            @RequestParam(defaultValue = "01-10-2013") String startDate,
            @RequestParam(defaultValue = "01-04-2026") String endDate) {
        return macroDataSyncService.syncCpiFromTcmb(startDate, endDate);
    }

    @PostMapping("/tcmb/ppi/sync")
    public MacroSyncResult syncPpi(
            @RequestParam(defaultValue = "01-10-2013") String startDate,
            @RequestParam(defaultValue = "01-04-2026") String endDate) {
        return macroDataSyncService.syncPpiFromTcmb(startDate, endDate);
    }

    @PostMapping("/tcmb/policy-rate/sync")
    public MacroSyncResult syncPolicyRate(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(defaultValue = "01-03-2026") String endDate) {
        return macroDataSyncService.syncPolicyRateFromTcmb(startDate, endDate);
    }

    @PostMapping("/tcmb/labor-market/sync")
    public MacroSyncResult syncLaborMarket(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(defaultValue = "01-03-2026") String endDate) {
        return macroDataSyncService.syncLaborMarketFromTcmb(startDate, endDate);
    }

    @PostMapping("/tcmb/consumer-confidence/sync")
    public MacroSyncResult syncConsumerConfidence(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(defaultValue = "01-03-2026") String endDate) {
        return macroDataSyncService.syncConsumerConfidenceFromTcmb(startDate, endDate);
    }

    @PostMapping("/tcmb/current-account/sync")
    public MacroSyncResult syncCurrentAccount(
            @RequestParam(defaultValue = "01-09-2013") String startDate,
            @RequestParam(defaultValue = "01-03-2026") String endDate) {
        return macroDataSyncService.syncCurrentAccountFromTcmb(startDate, endDate);
    }
}
