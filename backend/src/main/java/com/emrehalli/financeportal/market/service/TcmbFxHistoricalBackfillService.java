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
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisCacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    private static final int SAVE_BATCH_SIZE = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final IntervalType INTERVAL_TYPE = IntervalType.ONE_DAY;
    private static final SourceName SOURCE_NAME = SourceName.TCMB;

    private final TcmbHistoricalFxProvider tcmbHistoricalFxProvider;
    private final TcmbHistoricalFxMapper tcmbHistoricalFxMapper;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final MarketProperties marketProperties;
    private final TechnicalAnalysisCacheEvictionService technicalAnalysisCacheEvictionService;

    @Transactional
    public BackfillSummary backfillAll() {
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

            for (ResolvedDefinition resolvedDefinition : resolvedDefinitions) {
                log.info("TCMB FX historical instrument processing. instrumentCode={}, seriesCode={}, startDate={}, endDate={}",
                        resolvedDefinition.definition().instrumentCode(),
                        resolvedDefinition.definition().seriesCode(),
                        startDate,
                        effectiveEndDate);
            }

            log.info("Fetching TCMB FX historical chunk group. startDate={}, endDate={}, seriesCodes={}",
                    startDate, effectiveEndDate, groupSeriesCodes);

            List<Map<String, Object>> rows = tcmbHistoricalFxProvider.fetchHistoricalChunked(
                    groupSeriesCodes,
                    startDate,
                    effectiveEndDate
            );
            totalRows += rows.size();
            log.info("TCMB FX historical rows fetched. startDate={}, endDate={}, rowCount={}",
                    startDate, effectiveEndDate, rows.size());

            List<TcmbHistoricalFxValue> mappedValues = tcmbHistoricalFxMapper.mapRows(rows, groupDefinitions);
            totalMappedValues += mappedValues.size();
            log.info("TCMB FX historical rows mapped. startDate={}, endDate={}, mappedValuesCount={}",
                    startDate, effectiveEndDate, mappedValues.size());

            Map<String, MarketInstrument> instrumentsByCode = new LinkedHashMap<>();
            for (ResolvedDefinition resolvedDefinition : resolvedDefinitions) {
                instrumentsByCode.put(resolvedDefinition.definition().instrumentCode(), resolvedDefinition.instrument());
            }

            List<MarketInstrument> instruments = new ArrayList<>(instrumentsByCode.values());
            Instant rangeStart = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant rangeEnd = effectiveEndDate.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);
            List<MarketPriceHistory> existingRecords = instruments.isEmpty()
                    ? List.of()
                    : marketPriceHistoryRepository.findByInstrumentInAndIntervalTypeAndSourceNameAndPriceTimestampBetween(
                            instruments,
                            INTERVAL_TYPE,
                            SOURCE_NAME,
                            rangeStart,
                            rangeEnd
                    );
            java.util.Set<String> existingKeys = new java.util.HashSet<>();
            for (MarketPriceHistory existingRecord : existingRecords) {
                existingKeys.add(buildHistoryKey(
                        existingRecord.getInstrument().getId(),
                        existingRecord.getPriceTimestamp()
                ));
            }

            List<MarketPriceHistory> recordsToSave = new ArrayList<>();

            for (TcmbHistoricalFxValue value : mappedValues) {
                MarketInstrument instrument = instrumentsByCode.get(value.instrumentCode());
                if (instrument == null) {
                    totalMissingInstruments++;
                    log.warn("Skipping TCMB FX historical row because instrument is missing. instrumentCode={}, seriesCode={}, priceDate={}",
                            value.instrumentCode(), value.seriesCode(), value.priceDate());
                    continue;
                }

                Instant priceTimestamp = value.priceDate().atStartOfDay().toInstant(ZoneOffset.UTC);
                String historyKey = buildHistoryKey(instrument.getId(), priceTimestamp);
                if (existingKeys.contains(historyKey)) {
                    totalDuplicates++;
                    continue;
                }

                existingKeys.add(historyKey);
                recordsToSave.add(MarketPriceHistory.builder()
                        .instrument(instrument)
                        .closePrice(value.priceValue())
                        .priceTimestamp(priceTimestamp)
                        .intervalType(INTERVAL_TYPE)
                        .sourceName(SOURCE_NAME)
                        .build());
            }

            int groupSaved = saveInBatches(recordsToSave);
            totalSaved += groupSaved;
            if (groupSaved > 0) {
                log.info("TCMB FX historical group save completed. startDate={}, endDate={}, groupSaved={}",
                        startDate, effectiveEndDate, groupSaved);
            }
        }

        int totalParseOrNullSkips = Math.max(totalRows * definitions.size() - totalMappedValues, 0);
        log.info("TCMB FX historical backfill completed. rows={}, saved={}, duplicatesSkipped={}, parseOrNullSkipped={}, missingInstruments={}",
                totalRows, totalSaved, totalDuplicates, totalParseOrNullSkips, totalMissingInstruments);
        if (totalSaved > 0) {
            technicalAnalysisCacheEvictionService.evictAllTechnicalCaches();
        }

        return new BackfillSummary(
                totalRows,
                totalSaved,
                totalDuplicates,
                totalParseOrNullSkips,
                totalMissingInstruments
        );
    }

    @Transactional
    public BackfillSummary backfill() {
        return backfillAll();
    }

    private Map<LocalDate, List<ResolvedDefinition>> resolveDefinitionsByStartDate(
            List<TcmbFxSeriesDefinition> definitions,
            LocalDate configuredStartDate,
            LocalDate effectiveEndDate
    ) {
        Map<LocalDate, List<ResolvedDefinition>> groupedDefinitions = new LinkedHashMap<>();

        for (TcmbFxSeriesDefinition definition : definitions) {
            Optional<MarketInstrument> instrumentOptional = marketInstrumentRepository
                    .findByInstrumentCodeIgnoreCaseAndSourceName(definition.instrumentCode(), SOURCE_NAME);
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
                    .map(timestamp -> timestamp.atZone(ZoneOffset.UTC).toLocalDate())
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

    private int saveInBatches(List<MarketPriceHistory> recordsToSave) {
        int saved = 0;
        int batchIndex = 0;

        for (int start = 0; start < recordsToSave.size(); start += SAVE_BATCH_SIZE) {
            int end = Math.min(start + SAVE_BATCH_SIZE, recordsToSave.size());
            List<MarketPriceHistory> batch = recordsToSave.subList(start, end);
            marketPriceHistoryRepository.saveAll(batch);
            batchIndex++;
            saved += batch.size();
            log.info("TCMB FX historical batch saved. batchIndex={}, batchSize={}, totalSaved={}",
                    batchIndex, batch.size(), saved);
        }

        return saved;
    }

    private String buildHistoryKey(Long instrumentId, Instant priceTimestamp) {
        return instrumentId + "|" + priceTimestamp;
    }

    public record BackfillSummary(
            int rows,
            int saved,
            int duplicatesSkipped,
            int parseOrNullSkipped,
            int missingInstruments
    ) {
    }

    private record ResolvedDefinition(TcmbFxSeriesDefinition definition, MarketInstrument instrument) {
    }
}




