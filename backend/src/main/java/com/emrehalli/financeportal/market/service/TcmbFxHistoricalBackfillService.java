package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbFxSeriesDefinition;
import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbFxSeriesDefinitions;
import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbHistoricalFxProvider;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbHistoricalFxValue;
import com.emrehalli.financeportal.market.provider.fx.tcmb.mapper.TcmbHistoricalFxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TcmbFxHistoricalBackfillService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final IntervalType INTERVAL_TYPE = IntervalType.ONE_DAY;
    private static final SourceName SOURCE_NAME = SourceName.TCMB;

    private final TcmbHistoricalFxProvider tcmbHistoricalFxProvider;
    private final TcmbHistoricalFxMapper tcmbHistoricalFxMapper;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final MarketProperties marketProperties;

    @Transactional
    public void backfillAll() {
        List<TcmbFxSeriesDefinition> definitions = TcmbFxSeriesDefinitions.DEFAULT_DEFINITIONS;
        List<String> allSeriesCodes = definitions.stream()
                .map(TcmbFxSeriesDefinition::seriesCode)
                .toList();
        LocalDate configuredStartDate = parseConfiguredDate(marketProperties.getProviders().getTcmb().getStartDate());
        LocalDate configuredEndDate = parseConfiguredDate(marketProperties.getProviders().getTcmb().getEndDate());
        LocalDate effectiveEndDate = configuredEndDate.isBefore(LocalDate.now()) ? configuredEndDate : LocalDate.now();

        log.info("TCMB FX historical backfill started. seriesCount={}, configuredStartDate={}, effectiveEndDate={}",
                allSeriesCodes.size(), configuredStartDate, effectiveEndDate);

        Map<LocalDate, List<ResolvedDefinition>> groupedDefinitions = resolveDefinitionsByStartDate(definitions, configuredStartDate, effectiveEndDate);

        int totalRows = 0;
        int totalMappedValues = 0;
        int totalSaved = 0;
        int totalDuplicates = 0;
        int totalMissingInstruments = 0;

        for (Map.Entry<LocalDate, List<ResolvedDefinition>> entry : groupedDefinitions.entrySet()) {
            LocalDate startDate = entry.getKey();
            List<ResolvedDefinition> resolvedDefinitions = entry.getValue();

            if (resolvedDefinitions.isEmpty() || startDate.isAfter(effectiveEndDate)) {
                continue;
            }

            List<TcmbFxSeriesDefinition> groupDefinitions = resolvedDefinitions.stream()
                    .map(ResolvedDefinition::definition)
                    .toList();
            List<String> groupSeriesCodes = groupDefinitions.stream()
                    .map(TcmbFxSeriesDefinition::seriesCode)
                    .toList();

            log.info("Fetching TCMB FX historical chunk. startDate={}, endDate={}, seriesCodes={}",
                    startDate, effectiveEndDate, groupSeriesCodes);

            List<Map<String, Object>> rows = tcmbHistoricalFxProvider.fetchHistoricalChunked(
                    groupSeriesCodes,
                    startDate,
                    effectiveEndDate
            );
            totalRows += rows.size();

            List<TcmbHistoricalFxValue> mappedValues = tcmbHistoricalFxMapper.mapRows(rows, groupDefinitions);
            totalMappedValues += mappedValues.size();

            Map<String, MarketInstrument> instrumentsByCode = new LinkedHashMap<>();
            for (ResolvedDefinition resolvedDefinition : resolvedDefinitions) {
                instrumentsByCode.put(resolvedDefinition.definition().instrumentCode(), resolvedDefinition.instrument());
            }

            for (TcmbHistoricalFxValue value : mappedValues) {
                MarketInstrument instrument = instrumentsByCode.get(value.instrumentCode());
                if (instrument == null) {
                    totalMissingInstruments++;
                    log.warn("Skipping TCMB FX historical row because instrument is missing. instrumentCode={}, seriesCode={}, priceDate={}",
                            value.instrumentCode(), value.seriesCode(), value.priceDate());
                    continue;
                }

                LocalDateTime priceTimestamp = value.priceDate().atStartOfDay();
                boolean exists = marketPriceHistoryRepository.existsByInstrumentAndIntervalTypeAndPriceTimestamp(
                        instrument,
                        INTERVAL_TYPE,
                        priceTimestamp
                );
                if (exists) {
                    totalDuplicates++;
                    continue;
                }

                marketPriceHistoryRepository.save(MarketPriceHistory.builder()
                        .instrument(instrument)
                        .closePrice(value.priceValue())
                        .priceTimestamp(priceTimestamp)
                        .intervalType(INTERVAL_TYPE)
                        .sourceName(SOURCE_NAME)
                        .build());
                totalSaved++;
            }
        }

        int totalParseOrNullSkips = Math.max(totalRows * definitions.size() - totalMappedValues, 0);
        log.info("TCMB FX historical backfill completed. rows={}, saved={}, duplicatesSkipped={}, parseOrNullSkipped={}, missingInstruments={}",
                totalRows, totalSaved, totalDuplicates, totalParseOrNullSkips, totalMissingInstruments);
    }

    @Transactional
    public void backfill() {
        backfillAll();
    }

    private Map<LocalDate, List<ResolvedDefinition>> resolveDefinitionsByStartDate(
            List<TcmbFxSeriesDefinition> definitions,
            LocalDate configuredStartDate,
            LocalDate effectiveEndDate
    ) {
        Map<LocalDate, List<ResolvedDefinition>> groupedDefinitions = new LinkedHashMap<>();

        for (TcmbFxSeriesDefinition definition : definitions) {
            Optional<MarketInstrument> instrumentOptional = marketInstrumentRepository.findByInstrumentCodeIgnoreCase(definition.instrumentCode());
            if (instrumentOptional.isEmpty()) {
                log.warn("Skipping TCMB FX historical backfill because instrument is missing. instrumentCode={}, seriesCode={}",
                        definition.instrumentCode(), definition.seriesCode());
                continue;
            }

            MarketInstrument instrument = instrumentOptional.get();
            LocalDate startDate = marketPriceHistoryRepository
                    .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                            instrument,
                            INTERVAL_TYPE,
                            SOURCE_NAME
                    )
                    .map(MarketPriceHistory::getPriceTimestamp)
                    .map(LocalDateTime::toLocalDate)
                    .map(date -> date.plusDays(1))
                    .orElse(configuredStartDate);

            log.info("Resolved TCMB FX historical range for instrumentCode={}. startDate={}, endDate={}",
                    definition.instrumentCode(), startDate, effectiveEndDate);

            groupedDefinitions.computeIfAbsent(startDate, ignored -> new ArrayList<>())
                    .add(new ResolvedDefinition(definition, instrument));
        }

        return groupedDefinitions;
    }

    private LocalDate parseConfiguredDate(String value) {
        return LocalDate.parse(value, DATE_FORMATTER);
    }

    private record ResolvedDefinition(TcmbFxSeriesDefinition definition, MarketInstrument instrument) {
    }
}
