package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.service.CommodityHistoryDerivationService;
import com.emrehalli.financeportal.market.service.CommodityHistoryDerivationService.DerivationResult;
import com.emrehalli.financeportal.market.service.CommodityService;
import com.emrehalli.financeportal.market.service.CommodityService.DerivedCommodityResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@Tag(name = "Admin - Emtia Yonetimi", description = "Emtia veri turetme ve gecmis doldurma islemleri")
public class CommodityAdminController {

    private final CommodityService commodityService;
    private final CommodityHistoryDerivationService historyDerivationService;

    @Operation(summary = "Turetilmis emtia hesapla", description = "Zamanlayici beklemeden turetilmis emtia fiyat hesaplamasini manuel tetikler")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Turetme islemi basariyla tamamlandi"))
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

    @Operation(summary = "Emtia durum kontrolu", description = "Emtia hesaplama icin gerekli girdi verilerinin mevcut durumunu gosterir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Durum bilgisi basariyla getirildi"))
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

    @Operation(summary = "Emtia gecmis verisi doldur", description = "Turk altin/gumus enstrumanlari icin tarihsel fiyat kayitlarini geriye donuk doldurur")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gecmis veri doldurma islemi tamamlandi"))
    @PostMapping("/history/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> backfillHistory(@RequestParam(defaultValue = "365") int days) {
        log.info("Manual history backfill triggered by admin. days={}", days);

        Map<String, DerivationResult> results = historyDerivationService.deriveHistory(days);

        int totalSaved   = results.values().stream().mapToInt(DerivationResult::saved).sum();
        int totalSkipped = results.values().stream().mapToInt(DerivationResult::skipped).sum();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("days",         days);
        response.put("totalSaved",   totalSaved);
        response.put("totalSkipped", totalSkipped);
        response.put("bySymbol",     results);
        response.put("triggeredAt",  LocalDateTime.now());

        if (totalSaved == 0 && totalSkipped == 0) {
            response.put("warning", "No source history found. Populate GOLD_USD/SILVER_USD and USDTRY ONE_DAY history first.");
        }

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

