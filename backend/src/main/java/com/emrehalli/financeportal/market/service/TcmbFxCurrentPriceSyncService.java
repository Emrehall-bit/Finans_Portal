package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbFxSeriesDefinition;
import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbFxSeriesDefinitions;
import com.emrehalli.financeportal.market.provider.fx.tcmb.client.TcmbEvdsClient;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbHistoricalFxValue;
import com.emrehalli.financeportal.market.provider.fx.tcmb.mapper.TcmbHistoricalFxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TcmbFxCurrentPriceSyncService {

    private static final int LOOKBACK_DAYS = 7;

    private final TcmbEvdsClient tcmbEvdsClient;
    private final TcmbHistoricalFxMapper tcmbHistoricalFxMapper;
    private final FxService fxService;

    @Transactional
    public void syncCurrentPrices() {
        List<TcmbFxSeriesDefinition> definitions = TcmbFxSeriesDefinitions.CURRENT_SYNC_DEFINITIONS;
        List<String> seriesCodes = definitions.stream()
                .map(TcmbFxSeriesDefinition::seriesCode)
                .toList();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(LOOKBACK_DAYS - 1L);

        TcmbEvdsResponse response = tcmbEvdsClient.fetch(seriesCodes, startDate, endDate);
        List<TcmbHistoricalFxValue> mappedValues = tcmbHistoricalFxMapper.mapRows(response.getItems(), definitions);

        Map<String, TcmbHistoricalFxValue> latestByInstrumentCode = new LinkedHashMap<>();
        for (TcmbHistoricalFxValue value : mappedValues) {
            TcmbHistoricalFxValue current = latestByInstrumentCode.get(value.instrumentCode());
            if (current == null || value.priceDate().isAfter(current.priceDate())) {
                latestByInstrumentCode.put(value.instrumentCode(), value);
            }
        }

        Map<String, CurrentFxSnapshot> snapshotsByCurrency = new LinkedHashMap<>();
        for (TcmbHistoricalFxValue value : latestByInstrumentCode.values()) {
            String currencyCode = extractCurrencyCode(value.instrumentCode());
            CurrentFxSnapshot snapshot = snapshotsByCurrency.computeIfAbsent(currencyCode, ignored -> new CurrentFxSnapshot());
            LocalDateTime timestamp = value.priceDate().atStartOfDay();
            if (value.instrumentCode().endsWith(":BUY")) {
                snapshot.buyPrice = value.priceValue();
                snapshot.buyTimestamp = timestamp;
            } else if (value.instrumentCode().endsWith(":SELL")) {
                snapshot.sellPrice = value.priceValue();
                snapshot.sellTimestamp = timestamp;
            }
        }

        List<FxRateDto> rates = snapshotsByCurrency.entrySet().stream()
                .map(entry -> FxRateDto.builder()
                        .sourceName(SourceName.TCMB)
                        .currencyCode(entry.getKey())
                        .buyPrice(entry.getValue().buyPrice)
                        .sellPrice(entry.getValue().sellPrice)
                        .dataTimestamp(resolveTimestamp(entry.getValue()))
                        .build())
                .filter(rate -> rate.getBuyPrice() != null || rate.getSellPrice() != null)
                .toList();

        fxService.saveAll(rates);
    }

    private String extractCurrencyCode(String instrumentCode) {
        String[] parts = instrumentCode.split(":");
        return parts.length >= 2 ? parts[1] : instrumentCode;
    }

    private LocalDateTime resolveTimestamp(CurrentFxSnapshot snapshot) {
        if (snapshot.sellTimestamp != null) {
            return snapshot.sellTimestamp;
        }
        return snapshot.buyTimestamp;
    }

    private static final class CurrentFxSnapshot {
        private java.math.BigDecimal buyPrice;
        private LocalDateTime buyTimestamp;
        private java.math.BigDecimal sellPrice;
        private LocalDateTime sellTimestamp;
    }
}



