package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import com.emrehalli.financeportal.market.persistence.mapper.MarketHistoryPersistenceMapper;
import com.emrehalli.financeportal.market.persistence.repository.MarketHistoryRepository;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MarketHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryService.class);

    private final MarketHistoryRepository marketHistoryRepository;
    private final MarketHistoryPersistenceMapper marketHistoryPersistenceMapper;
    private final SymbolNormalizer symbolNormalizer;

    public MarketHistoryService(MarketHistoryRepository marketHistoryRepository,
                                MarketHistoryPersistenceMapper marketHistoryPersistenceMapper,
                                SymbolNormalizer symbolNormalizer) {
        this.marketHistoryRepository = marketHistoryRepository;
        this.marketHistoryPersistenceMapper = marketHistoryPersistenceMapper;
        this.symbolNormalizer = symbolNormalizer;
    }

    public MarketHistoryPersistenceResult persistHistory(DataSource source, List<MarketHistoryRecord> records) {
        List<MarketHistoryRecord> safeRecords = records == null ? List.of() : records;
        List<MarketHistoryRecord> deduplicatedRecords = deduplicate(safeRecords);
        ExistingHistoryWindow existingHistoryWindow = preloadExistingHistory(source, deduplicatedRecords);

        List<MarketHistoryEntity> newEntities = deduplicatedRecords.stream()
                .filter(record -> !existingHistoryWindow.contains(record))
                .map(marketHistoryPersistenceMapper::toEntity)
                .toList();

        if (!newEntities.isEmpty()) {
            marketHistoryRepository.saveAll(newEntities);
        }

        int savedCount = newEntities.size();
        int skippedDuplicateCount = safeRecords.size() - savedCount;
        log.info(
                "Market history persistence completed: source={}, received={}, saved={}, skippedDuplicate={}",
                source,
                safeRecords.size(),
                savedCount,
                skippedDuplicateCount
        );
        return new MarketHistoryPersistenceResult(source, safeRecords.size(), savedCount, skippedDuplicateCount);
    }

    public List<MarketHistoryRecord> getHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        return getHistory(symbol, null, startDate, endDate);
    }

    public List<MarketHistoryRecord> getHistory(String symbol, DataSource source, LocalDate startDate, LocalDate endDate) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);

        return findHistoryEntities(canonicalSymbol, source, startDate, endDate).stream()
                .map(marketHistoryPersistenceMapper::toRecord)
                .toList();
    }

    private List<MarketHistoryRecord> deduplicate(List<MarketHistoryRecord> records) {
        Map<String, MarketHistoryRecord> uniqueRecords = new LinkedHashMap<>();
        for (MarketHistoryRecord record : records) {
            String key = historyKey(record.symbol(), record.source(), record.priceDate());
            uniqueRecords.putIfAbsent(key, record);
        }
        return List.copyOf(uniqueRecords.values());
    }

    private ExistingHistoryWindow preloadExistingHistory(DataSource source, List<MarketHistoryRecord> records) {
        if (source == null || records.isEmpty()) {
            return ExistingHistoryWindow.empty();
        }

        Set<String> symbols = records.stream()
                .map(MarketHistoryRecord::symbol)
                .collect(java.util.stream.Collectors.toSet());

        LocalDate minDate = records.stream()
                .map(MarketHistoryRecord::priceDate)
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate maxDate = records.stream()
                .map(MarketHistoryRecord::priceDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        Set<String> existingKeys = marketHistoryRepository
                .findBySourceAndSymbolInAndPriceDateBetween(source, symbols, minDate, maxDate)
                .stream()
                .map(entity -> historyKey(entity.getSymbol(), entity.getSource(), entity.getPriceDate()))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        return new ExistingHistoryWindow(existingKeys);
    }

    private String historyKey(String symbol, DataSource source, LocalDate priceDate) {
        return symbol + "|" + source + "|" + priceDate;
    }

    private List<MarketHistoryEntity> findHistoryEntities(String canonicalSymbol,
                                                          DataSource source,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        return Optional.ofNullable(source)
                .map(value -> marketHistoryRepository.findBySymbolAndSourceAndPriceDateBetweenOrderByPriceDateAsc(
                        canonicalSymbol,
                        value,
                        startDate,
                        endDate
                ))
                .orElseGet(() -> marketHistoryRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                        canonicalSymbol,
                        startDate,
                        endDate
                ));
    }

    private record ExistingHistoryWindow(Set<String> existingKeys) {
        private static ExistingHistoryWindow empty() {
            return new ExistingHistoryWindow(Set.of());
        }

        private boolean contains(MarketHistoryRecord record) {
            return existingKeys.contains(record.symbol() + "|" + record.source() + "|" + record.priceDate());
        }
    }
}
