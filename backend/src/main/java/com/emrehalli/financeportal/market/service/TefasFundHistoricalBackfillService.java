package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.fund.TefasProvider;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundListResponseItem;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundPriceResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TefasFundHistoricalBackfillService {

    private static final int MAX_PERIOD_MONTHS = 60;
    private static final int FUND_CHUNK_SIZE = 50;
    private static final long CHUNK_DELAY_MS = 300L;
    private static final IntervalType INTERVAL_TYPE = IntervalType.ONE_DAY;
    private static final SourceName SOURCE_NAME = SourceName.TEFAS;

    private final TefasProvider tefasProvider;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;

    @Transactional
    public BackfillResult runBackfill(String fundCode, Integer periyod) {
        try {
            long startedAt = System.currentTimeMillis();
            log.info("Backfill baÅŸladÄ±, fundCode: {}", fundCode);

            List<String> fundCodes;
            if (fundCode != null && !fundCode.isBlank()) {
                String normalizedFundCode = normalizeCode(fundCode);
                seedSingleFundInstrument(normalizedFundCode);
                fundCodes = List.of(normalizedFundCode);
            } else {
                List<TefasFundListResponseItem> allFunds = tefasProvider.fetchAllFunds();
                log.info("fetchAllFunds sonucu: {} fon", allFunds.size());
                seedFundInstruments(allFunds);
                fundCodes = resolveFundCodes(allFunds);
            }
            int processedFunds = 0;
            int savedRecords = 0;
            int skippedDuplicates = 0;

            for (int start = 0; start < fundCodes.size(); start += FUND_CHUNK_SIZE) {
                int end = Math.min(start + FUND_CHUNK_SIZE, fundCodes.size());
                List<String> chunk = fundCodes.subList(start, end);

                for (String code : chunk) {
                    BackfillCounters counters = backfillSingleFund(code, periyod);
                    processedFunds += counters.processedFunds();
                    savedRecords += counters.savedRecords();
                    skippedDuplicates += counters.skippedDuplicates();
                }

                if (end < fundCodes.size()) {
                    sleepBetweenChunks();
                }
            }

            return new BackfillResult(
                    processedFunds,
                    savedRecords,
                    skippedDuplicates,
                    System.currentTimeMillis() - startedAt
            );
        } catch (Exception e) {
            log.error("Backfill hatasÄ±: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<String> resolveFundCodes(List<TefasFundListResponseItem> allFunds) {
        try {
            return allFunds.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> item.getFonKodu() != null && !item.getFonKodu().isBlank())
                    .map(TefasFundListResponseItem::getFonKodu)
                    .map(this::normalizeCode)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.error("Backfill hatasÄ±: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void seedFundInstruments(List<TefasFundListResponseItem> allFunds) {
        List<String> allCodes = allFunds.stream()
                .filter(Objects::nonNull)
                .map(TefasFundListResponseItem::getFonKodu)
                .filter(Objects::nonNull)
                .filter(code -> !code.isBlank())
                .map(this::normalizeCode)
                .distinct()
                .toList();

        if (allCodes.isEmpty()) {
            log.info("TEFAS fund seed: eklenecek instrument bulunamadÄ±");
            return;
        }

        List<MarketInstrument> existing = marketInstrumentRepository
                .findAllByInstrumentCodeInAndSourceName(allCodes, SOURCE_NAME);

        Set<String> existingCodes = existing.stream()
                .map(MarketInstrument::getInstrumentCode)
                .collect(Collectors.toSet());

        List<MarketInstrument> toSave = allFunds.stream()
                .filter(Objects::nonNull)
                .filter(f -> f.getFonKodu() != null && !f.getFonKodu().isBlank())
                .filter(f -> !existingCodes.contains(normalizeCode(f.getFonKodu())))
                .map(f -> {
                    MarketInstrument mi = new MarketInstrument();
                    mi.setInstrumentCode(normalizeCode(f.getFonKodu()));
                    mi.setInstrumentName(f.getFonUnvan() != null && !f.getFonUnvan().isBlank()
                            ? f.getFonUnvan()
                            : normalizeCode(f.getFonKodu()));
                    mi.setInstrumentType(InstrumentType.FUND);
                    mi.setSourceName(SOURCE_NAME);
                    return mi;
                })
                .toList();

        if (!toSave.isEmpty()) {
            marketInstrumentRepository.saveAll(toSave);
            log.info("TEFAS fund seed tamamlandÄ±: {} yeni instrument eklendi", toSave.size());
        } else {
            log.info("TEFAS fund seed: tÃ¼m instrumentlar zaten mevcut");
        }
    }

    private void seedSingleFundInstrument(String fundCode) {
        try {
            boolean exists = marketInstrumentRepository
                    .findByInstrumentCodeAndSourceName(fundCode, SOURCE_NAME)
                    .filter(instrument -> instrument.getInstrumentType() == InstrumentType.FUND)
                    .isPresent();

            if (exists) {
                log.info("TEFAS fund seed: instrument zaten mevcut, fundCode={}", fundCode);
                return;
            }

            MarketInstrument instrument = new MarketInstrument();
            instrument.setInstrumentCode(fundCode);
            instrument.setInstrumentName(fundCode);
            instrument.setInstrumentType(InstrumentType.FUND);
            instrument.setSourceName(SOURCE_NAME);
            marketInstrumentRepository.save(instrument);
            log.info("TEFAS single fund seed tamamlandÄ±: instrument eklendi, fundCode={}", fundCode);
        } catch (Exception e) {
            log.error("Backfill hatasÄ±: {}", e.getMessage(), e);
            throw e;
        }
    }

    private BackfillCounters backfillSingleFund(String fundCode, Integer requestedPeriod) {
        try {
            log.info("backfillSingleFund baÅŸladÄ±: {}", fundCode);
            log.info("Instrument aranÄ±yor: {}", fundCode);
            Optional<MarketInstrument> instrumentOptional = marketInstrumentRepository
                    .findByInstrumentCodeAndSourceName(fundCode, SOURCE_NAME)
                    .filter(instrument -> instrument.getInstrumentType() == InstrumentType.FUND);
            log.info("Instrument bulundu: {}", instrumentOptional.isPresent());

            if (instrumentOptional.isEmpty()) {
                log.warn("Skipping TEFAS fund historical backfill because instrument is missing. fundCode={}", fundCode);
                return new BackfillCounters(0, 0, 0);
            }

            MarketInstrument instrument = instrumentOptional.get();
            LocalDate lastDate = marketPriceHistoryRepository
                    .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                            instrument,
                            INTERVAL_TYPE,
                            SOURCE_NAME
                    )
                    .map(MarketPriceHistory::getPriceTimestamp)
                    .map(timestamp -> timestamp.atZone(ZoneOffset.UTC).toLocalDate())
                    .orElse(null);

            int effectivePeriod = resolvePeriod(lastDate, requestedPeriod);
            log.info("fetchFundHistory Ã§aÄŸrÄ±lÄ±yor: {} periyod: {}", fundCode, effectivePeriod);
            List<TefasFundPriceResponseItem> history = tefasProvider.fetchFundHistory(fundCode, effectivePeriod);
            log.info("History kayÄ±t sayÄ±sÄ±: {}", history.size());
            log.info("fetchFundHistory sonucu: {} kayÄ±t", history.size());
            if (history.isEmpty()) {
                return new BackfillCounters(1, 0, 0);
            }

            Instant rangeStart = history.stream()
                    .filter(Objects::nonNull)
                    .map(TefasFundPriceResponseItem::getTarih)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .map(LocalDate::parse)
                    .min(LocalDate::compareTo)
                    .map(date -> date.atStartOfDay().toInstant(ZoneOffset.UTC))
                    .orElse(null);

            Instant rangeEnd = history.stream()
                    .filter(Objects::nonNull)
                    .map(TefasFundPriceResponseItem::getTarih)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .map(LocalDate::parse)
                    .max(LocalDate::compareTo)
                    .map(date -> date.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC))
                    .orElse(null);

            Set<String> existingKeys = new HashSet<>();
            if (rangeStart != null && rangeEnd != null) {
                List<MarketPriceHistory> existingRecords =
                        marketPriceHistoryRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                                instrument,
                                INTERVAL_TYPE,
                                SOURCE_NAME,
                                rangeStart,
                                rangeEnd
                        );

                for (MarketPriceHistory existingRecord : existingRecords) {
                    existingKeys.add(buildHistoryKey(existingRecord.getInstrument().getId(), existingRecord.getPriceTimestamp()));
                }
            }

            List<MarketPriceHistory> recordsToSave = new ArrayList<>();
            int duplicates = 0;

            for (TefasFundPriceResponseItem item : history) {
                if (item == null || item.getTarih() == null || item.getTarih().isBlank() || item.getFiyat() == null) {
                    continue;
                }

                Instant priceTimestamp = LocalDate.parse(item.getTarih()).atStartOfDay().toInstant(ZoneOffset.UTC);
                String historyKey = buildHistoryKey(instrument.getId(), priceTimestamp);
                if (existingKeys.contains(historyKey)) {
                    duplicates++;
                    continue;
                }

                existingKeys.add(historyKey);
                recordsToSave.add(MarketPriceHistory.builder()
                        .instrument(instrument)
                        .intervalType(INTERVAL_TYPE)
                        .sourceName(SOURCE_NAME)
                        .closePrice(item.getFiyat())
                        .priceTimestamp(priceTimestamp)
                        .build());
            }

            log.info("Kaydedilecek kayÄ±t: {}", recordsToSave.size());
            log.info("recordsToSave: {}", recordsToSave.size());
            if (!recordsToSave.isEmpty()) {
                log.info("saveAll Ã§aÄŸrÄ±lÄ±yor");
                marketPriceHistoryRepository.saveAll(recordsToSave);
            }

            return new BackfillCounters(1, recordsToSave.size(), duplicates);
        } catch (Exception e) {
            log.error("backfillSingleFund hatasÄ± fundCode={}: {}", fundCode, e.getMessage(), e);
            log.error("Backfill hatasÄ±: {}", e.getMessage(), e);
            throw e;
        }
    }

    private int resolvePeriod(LocalDate lastDate, Integer requestedPeriod) {
        if (requestedPeriod != null) {
            return requestedPeriod;
        }

        if (lastDate == null) {
            return 60;
        }

        long monthsNeeded = ChronoUnit.MONTHS.between(lastDate, LocalDate.now()) + 1;
        long clamped = Math.max(1, Math.min(monthsNeeded, MAX_PERIOD_MONTHS));
        return (int) clamped;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String buildHistoryKey(Long instrumentId, Instant priceTimestamp) {
        return instrumentId + "|" + priceTimestamp;
    }

    private void sleepBetweenChunks() {
        try {
            Thread.sleep(CHUNK_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Backfill hatasÄ±: {}", exception.getMessage(), exception);
            log.warn("Interrupted while waiting between TEFAS fund backfill chunks.");
        }
    }

    public record BackfillResult(
            int processedFunds,
            int savedRecords,
            int skippedDuplicates,
            long durationMs
    ) {
    }

    private record BackfillCounters(
            int processedFunds,
            int savedRecords,
            int skippedDuplicates
    ) {
    }
}




