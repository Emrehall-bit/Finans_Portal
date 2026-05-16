package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MacroIndicator;
import com.emrehalli.financeportal.market.domain.entity.MacroObservation;
import com.emrehalli.financeportal.market.domain.enums.MacroFrequency;
import com.emrehalli.financeportal.market.domain.enums.MacroSourceName;
import com.emrehalli.financeportal.market.domain.enums.MacroUnit;
import com.emrehalli.financeportal.market.persistence.MacroIndicatorRepository;
import com.emrehalli.financeportal.market.persistence.MacroObservationRepository;
import com.emrehalli.financeportal.market.provider.macro.tcmb.TcmbMacroProvider;
import com.emrehalli.financeportal.market.provider.macro.tcmb.TcmbMacroSeries;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroIndicatorDef;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroObservationParseResult;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroSeriesRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MacroDataSyncService {

    private final TcmbMacroProvider tcmbMacroProvider;
    private final MacroIndicatorRepository indicatorRepository;
    private final MacroObservationRepository observationRepository;

    @Transactional
    public MacroSyncResult syncCpiFromTcmb(String startDate, String endDate) {
        return syncSeries(TcmbMacroSeries.cpi(startDate, endDate));
    }

    @Transactional
    public MacroSyncResult syncPpiFromTcmb(String startDate, String endDate) {
        return syncSeries(TcmbMacroSeries.ppi(startDate, endDate));
    }

    @Transactional
    public MacroSyncResult syncPolicyRateFromTcmb(String startDate, String endDate) {
        return syncSeries(TcmbMacroSeries.policyRate(startDate, endDate));
    }

    @Transactional
    public MacroSyncResult syncLaborMarketFromTcmb(String startDate, String endDate) {
        return syncSeries(TcmbMacroSeries.laborMarket(startDate, endDate));
    }

    @Transactional
    public MacroSyncResult syncConsumerConfidenceFromTcmb(String startDate, String endDate) {
        return syncSeries(TcmbMacroSeries.consumerConfidence(startDate, endDate));
    }

    @Transactional
    public MacroSyncResult syncCurrentAccountFromTcmb(String startDate, String endDate) {
        return syncSeries(TcmbMacroSeries.currentAccount(startDate, endDate));
    }

    @Transactional
    public Map<String, MacroSyncResult> syncAllFromTcmb() {
        Map<String, MacroSyncResult> results = new LinkedHashMap<>();
        results.put("CPI", syncCpiFromTcmb("01-10-2013", "01-04-2026"));
        results.put("PPI", syncPpiFromTcmb("01-10-2013", "01-04-2026"));
        results.put("POLICY_RATE", syncPolicyRateFromTcmb("01-09-2013", "01-03-2026"));
        results.put("LABOR_MARKET", syncLaborMarketFromTcmb("01-09-2013", "01-03-2026"));
        results.put("CONSUMER_CONFIDENCE", syncConsumerConfidenceFromTcmb("01-09-2013", "01-03-2026"));
        results.put("CURRENT_ACCOUNT", syncCurrentAccountFromTcmb("01-09-2013", "01-03-2026"));
        return results;
    }

    private MacroSyncResult syncSeries(MacroSeriesRequest request) {
        Map<String, MacroIndicator> indicatorByCode = new HashMap<>();
        for (MacroIndicatorDef def : request.indicators()) {
            indicatorByCode.put(def.code(), ensureIndicator(def.code(), def.name(), def.frequency(), def.unit()));
        }

        List<MacroObservationParseResult> parsed = tcmbMacroProvider.fetchSeries(request);

        int fetched = parsed.size();
        int saved = 0;
        int duplicates = 0;
        int skipped = 0;

        for (MacroObservationParseResult item : parsed) {
            MacroIndicator indicator = indicatorByCode.get(item.indicatorCode());
            if (indicator == null) {
                log.warn("No indicator definition for code={}, skipping observation period={} type={}",
                        item.indicatorCode(), item.periodLabel(), item.valueType());
                skipped++;
                continue;
            }
            try {
                if (observationRepository.existsByIndicatorAndObservationDateAndValueType(
                        indicator, item.observationDate(), item.valueType())) {
                    duplicates++;
                    continue;
                }
                observationRepository.save(MacroObservation.builder()
                        .indicator(indicator)
                        .observationDate(item.observationDate())
                        .periodLabel(item.periodLabel())
                        .value(item.value())
                        .valueType(item.valueType())
                        .source(MacroSourceName.TCMB_EVDS)
                        .build());
                saved++;
            } catch (Exception ex) {
                log.warn("Failed to save observation code={} period={} type={}: {}",
                        item.indicatorCode(), item.periodLabel(), item.valueType(), ex.getMessage());
                skipped++;
            }
        }

        List<String> codes = request.indicators().stream().map(MacroIndicatorDef::code).toList();
        log.info("TCMB macro sync completed indicatorCode={}: fetched={}, saved={}, duplicates={}, skipped={}",
                String.join(",", codes), fetched, saved, duplicates, skipped);

        return new MacroSyncResult(codes, fetched, saved, duplicates, skipped);
    }

    private MacroIndicator ensureIndicator(String code, String name, MacroFrequency frequency, MacroUnit unit) {
        return indicatorRepository.findByCode(code).orElseGet(() ->
                indicatorRepository.save(MacroIndicator.builder()
                        .code(code)
                        .name(name)
                        .source(MacroSourceName.TCMB_EVDS)
                        .frequency(frequency)
                        .unit(unit)
                        .active(true)
                        .build())
        );
    }
}
