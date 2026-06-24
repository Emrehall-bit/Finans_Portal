package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin diagnostic endpoint for commodity DB state.
 * GET /api/v1/admin/debug/commodities
 */
@RestController
@RequestMapping("/api/v1/admin/debug/commodities")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Emtia Diagnostik", description = "Emtia veri tanilama islemleri")
public class CommodityDiagnosticController {

    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceRepository priceRepository;

    @Operation(summary = "Emtia diagnostik raporu", description = "Emtia fiyatlandirma boru hattinin saglik durumunu analiz eder")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diagnostik raporu basariyla olusturuldu"))
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> diagnose() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. All COMMODITY instruments
        List<MarketInstrument> yahooInstruments = instrumentRepository
                .findAllByInstrumentTypeAndSourceName(InstrumentType.COMMODITY, SourceName.YAHOO_FINANCE);
        List<MarketInstrument> calculatedInstruments = instrumentRepository
                .findAllByInstrumentTypeAndSourceName(InstrumentType.COMMODITY, SourceName.CALCULATED);

        List<Map<String, Object>> instrumentRows = new ArrayList<>();
        for (MarketInstrument i : yahooInstruments) addInstrumentRow(instrumentRows, i);
        for (MarketInstrument i : calculatedInstruments) addInstrumentRow(instrumentRows, i);

        result.put("commodityInstrumentCount", instrumentRows.size());
        result.put("commodityInstruments", instrumentRows);

        // 2. USDTRY resolution â€” matches TCMB:USD:SELL, TCMB:USDTRY:SELL, AKBANK:USD:SELL, etc.
        List<MarketInstrument> usdtryCandidates = instrumentRepository
                .findAllByInstrumentTypeAndInstrumentCodeContainingIgnoreCase(InstrumentType.FX, "USD")
                .stream()
                .filter(i -> {
                    String code = i.getInstrumentCode().toUpperCase(java.util.Locale.ROOT);
                    return code.contains("SELL")
                            && !code.contains("EUR") && !code.contains("GBP")
                            && !code.contains("JPY") && !code.contains("CHF")
                            && !code.contains("AUD") && !code.contains("CAD");
                })
                .toList();

        List<Map<String, Object>> usdtryRows = new ArrayList<>();
        for (MarketInstrument i : usdtryCandidates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instrumentCode", i.getInstrumentCode());
            row.put("sourceName", i.getSourceName().name());
            MarketPrice latest = priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(i).orElse(null);
            row.put("latestPrice", latest != null ? latest.getPriceValue() : "NO PRICE");
            row.put("priceTimestamp", latest != null ? latest.getPriceTimestamp() : null);
            usdtryRows.add(row);
        }

        BigDecimal usdTry = usdtryCandidates.stream()
                .flatMap(i -> priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(i).stream())
                .max(Comparator.comparing(MarketPrice::getPriceTimestamp))
                .map(MarketPrice::getPriceValue)
                .orElse(null);

        result.put("usdtryCandidatesFound", usdtryCandidates.size());
        result.put("usdtryCandidates", usdtryRows);
        result.put("resolvedUsdTry", usdTry != null ? usdTry : "NULL â€” derived commodities will be skipped");

        // 3. BRENT price check (confirms Yahoo fetch is working)
        instrumentRepository.findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc("BRENT", InstrumentType.COMMODITY)
                .ifPresentOrElse(brent -> {
                    MarketPrice latest = priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(brent).orElse(null);
                    result.put("brentPrice", latest != null ? latest.getPriceValue() : "NO PRICE");
                    result.put("brentTimestamp", latest != null ? latest.getPriceTimestamp() : null);
                }, () -> result.put("brentPrice", "BRENT INSTRUMENT NOT IN DB"));

        // 4. Diagnosis summary
        boolean hasInstruments = !calculatedInstruments.isEmpty();
        boolean hasPrices = calculatedInstruments.stream()
                .anyMatch(i -> priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(i).isPresent());
        boolean usdtryOk = usdTry != null;

        String diagnosis;
        if (!hasInstruments) {
            diagnosis = "A) SEED YOK â€” V31 migration henÃ¼z Ã§alÄ±ÅŸmamÄ±ÅŸ veya calculated instruments DB'de yok";
        } else if (!hasPrices && !usdtryOk) {
            diagnosis = "C+D) USDTRY bulunamÄ±yor â€” FX servisi henÃ¼z USDTRY:SELL verisi kaydetmemiÅŸ. Calculation Ã§alÄ±ÅŸamÄ±yor.";
        } else if (!hasPrices) {
            diagnosis = "B) PRICE YOK â€” Instruments DB'de var ama market_prices kaydÄ± yok. Scheduler henÃ¼z hesaplama yapmamÄ±ÅŸ.";
        } else {
            diagnosis = "E) Endpoint/Frontend sorunu â€” Instruments ve prices DB'de mevcut, getAll() filtreliyor olabilir.";
        }
        result.put("diagnosis", diagnosis);
        result.put("checkedAt", LocalDateTime.now());

        log.info("[CommodityDiagnostic] commodityCount={}, usdtryCandidates={}, usdTry={}, diagnosis={}",
                instrumentRows.size(), usdtryCandidates.size(), usdTry, diagnosis);

        return result;
    }

    private void addInstrumentRow(List<Map<String, Object>> rows, MarketInstrument i) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", i.getId());
        row.put("instrumentCode", i.getInstrumentCode());
        row.put("instrumentName", i.getInstrumentName());
        row.put("instrumentType", i.getInstrumentType().name());
        row.put("sourceName", i.getSourceName().name());
        MarketPrice latest = priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(i).orElse(null);
        row.put("latestPrice", latest != null ? latest.getPriceValue() : "NO PRICE");
        row.put("priceSource", latest != null ? latest.getSourceName().name() : null);
        row.put("priceTimestamp", latest != null ? latest.getPriceTimestamp() : null);
        rows.add(row);
    }
}

