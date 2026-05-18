package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.service.CommodityService;
import com.emrehalli.financeportal.market.service.CommodityService.DerivedCommodityResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints for commodity diagnostics and manual triggers.
 */
@RestController
@RequestMapping("/api/v1/admin/markets/commodities")
@RequiredArgsConstructor
@Slf4j
public class CommodityAdminController {

    private final CommodityService commodityService;

    /**
     * Manually triggers derived commodity calculation without waiting for the scheduler.
     * Reads GOLD_USD and SILVER_USD from DB (persisted as INTERNAL by previous Yahoo fetches),
     * resolves USDTRY from FX instruments, and saves all 6 calculated commodity prices.
     *
     * <p>Response includes inputs used, saved count and saved symbols for diagnostics.</p>
     */
    @PostMapping("/derive-now")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> deriveNow() {
        log.info("Manual derive-now triggered by admin");

        BigDecimal goldUsdOz   = commodityService.resolveInputFromDb("GOLD_USD");
        BigDecimal silverUsdOz = commodityService.resolveInputFromDb("SILVER_USD");

        log.info("derive-now inputs from DB: goldUsdOz={}, silverUsdOz={}",
                goldUsdOz != null ? goldUsdOz : "NOT IN DB",
                silverUsdOz != null ? silverUsdOz : "NOT IN DB");

        DerivedCommodityResult result =
                commodityService.calculateAndSaveDerivedCommodities(goldUsdOz, silverUsdOz);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("goldUsdOz",     result.goldUsdOz()     != null ? result.goldUsdOz()     : "MISSING");
        response.put("silverUsdOz",   result.silverUsdOz()   != null ? result.silverUsdOz()   : "MISSING");
        response.put("usdTry",        result.usdTry()        != null ? result.usdTry()        : "MISSING");
        response.put("savedCount",    result.savedCount());
        response.put("savedSymbols",  result.savedSymbols());
        response.put("triggeredAt",   LocalDateTime.now());

        if (result.savedCount() == 0) {
            String missing = buildMissingInputsMessage(result);
            response.put("warning", "No derived commodities saved. " + missing);
        }

        return response;
    }

    /**
     * Diagnostic endpoint — shows all COMMODITY instruments and their latest prices.
     * GET /api/v1/admin/debug/commodities is the fuller version; this is a quick status check.
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> status() {
        BigDecimal goldUsdOz   = commodityService.resolveInputFromDb("GOLD_USD");
        BigDecimal silverUsdOz = commodityService.resolveInputFromDb("SILVER_USD");
        BigDecimal usdTry      = commodityService.resolveUsdTry();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("goldUsdOzInDb",   goldUsdOz   != null ? goldUsdOz   : "NOT IN DB");
        response.put("silverUsdOzInDb", silverUsdOz != null ? silverUsdOz : "NOT IN DB");
        response.put("usdTryInDb",      usdTry      != null ? usdTry      : "NOT IN DB");
        response.put("readyToCalculate", goldUsdOz != null && silverUsdOz != null && usdTry != null);
        response.put("checkedAt",        LocalDateTime.now());
        return response;
    }

    private String buildMissingInputsMessage(DerivedCommodityResult result) {
        StringBuilder sb = new StringBuilder("Missing: ");
        if (result.goldUsdOz() == null)   sb.append("goldUsdOz ");
        if (result.silverUsdOz() == null) sb.append("silverUsdOz ");
        if (result.usdTry() == null)      sb.append("usdTry ");
        return sb.toString().trim();
    }
}
