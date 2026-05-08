package com.emrehalli.financeportal.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import com.emrehalli.financeportal.market.persistence.mapper.MarketHistoryPersistenceMapper;
import com.emrehalli.financeportal.market.persistence.repository.MarketHistoryRepository;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MarketHistoryService implements MarketHistoryReader {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryService.class);
    private static final String MARKET_HISTORY_CACHE = "market_history";

    private final MarketHistoryRepository marketHistoryRepository;
    private final MarketHistoryPersistenceMapper marketHistoryPersistenceMapper;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final SymbolNormalizer symbolNormalizer;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MarketHistoryService(MarketHistoryRepository marketHistoryRepository,
                                MarketHistoryPersistenceMapper marketHistoryPersistenceMapper,
                                CacheManager cacheManager,
                                ObjectMapper objectMapper,
                                SymbolNormalizer symbolNormalizer,
                                JdbcTemplate jdbcTemplate) {
        this.marketHistoryRepository = marketHistoryRepository;
        this.marketHistoryPersistenceMapper = marketHistoryPersistenceMapper;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.symbolNormalizer = symbolNormalizer;
        this.jdbcTemplate = jdbcTemplate;
    }

    MarketHistoryService(MarketHistoryRepository marketHistoryRepository,
                         MarketHistoryPersistenceMapper marketHistoryPersistenceMapper,
                         CacheManager cacheManager,
                         ObjectMapper objectMapper,
                         SymbolNormalizer symbolNormalizer) {
        this(marketHistoryRepository, marketHistoryPersistenceMapper, cacheManager, objectMapper, symbolNormalizer, null);
    }

    public MarketHistoryPersistenceResult persistHistory(DataSource source, List<MarketHistoryRecord> records) {
        List<MarketHistoryRecord> safeRecords = records == null ? List.of() : records;
        List<MarketHistoryRecord> validatedRecords = filterInvalidConstantPriceSeries(source, safeRecords);
        List<MarketHistoryRecord> deduplicatedRecords = deduplicate(validatedRecords);
        log.info(
                "Market history persistence candidates: source={}, receivedCount={}, validatedCount={}, candidateCount={}",
                source,
                safeRecords.size(),
                validatedRecords.size(),
                deduplicatedRecords.size()
        );
        int savedCount;
        if (jdbcTemplate == null) {
            ExistingHistoryWindow existingHistoryWindow = preloadExistingHistory(source, deduplicatedRecords);
            List<MarketHistoryEntity> newEntities = deduplicatedRecords.stream()
                    .filter(record -> !existingHistoryWindow.contains(record))
                    .map(marketHistoryPersistenceMapper::toEntity)
                    .toList();
            log.info("Market history persistence pre-insert: source={}, candidateCount={}, insertableCount={}", source, deduplicatedRecords.size(), newEntities.size());
            savedCount = insertIgnoringConflicts(newEntities);
        } else {
            List<MarketHistoryEntity> candidateEntities = deduplicatedRecords.stream()
                    .map(marketHistoryPersistenceMapper::toEntity)
                    .toList();
            log.info("Market history persistence pre-insert: source={}, candidateCount={}", source, candidateEntities.size());
            savedCount = insertIgnoringConflicts(candidateEntities);
        }
        if (savedCount > 0) {
            clearHistoryCache();
        }

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

    @Override
    public List<MarketHistoryRecord> getHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        return getHistory(symbol, null, startDate, endDate);
    }

    @Override
    public List<MarketHistoryRecord> getHistory(String symbol, DataSource source, LocalDate startDate, LocalDate endDate) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);
        String cacheKey = getCacheKey(canonicalSymbol, source, startDate, endDate);

        List<MarketHistoryRecord> cachedHistory = readCachedHistory(cacheKey);
        if (cachedHistory != null) {
            logHistoryRead("cache", canonicalSymbol, source, startDate, endDate, cachedHistory);
            return cachedHistory;
        }

        List<MarketHistoryRecord> history = findHistoryEntities(canonicalSymbol, source, startDate, endDate).stream()
                .map(marketHistoryPersistenceMapper::toRecord)
                .toList();
        logHistoryRead("db", canonicalSymbol, source, startDate, endDate, history);
        writeCachedHistory(cacheKey, history);
        return history;
    }

    public long countHistory(String symbol, DataSource source) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);
        if (source == null) {
            return 0L;
        }
        return marketHistoryRepository.countBySymbolAndSource(canonicalSymbol, source);
    }

    public LocalDate findMinPriceDate(String symbol, DataSource source) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);
        return source == null ? null : marketHistoryRepository.findMinPriceDateBySymbolAndSource(canonicalSymbol, source);
    }

    public LocalDate findMaxPriceDate(String symbol, DataSource source) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);
        return source == null ? null : marketHistoryRepository.findMaxPriceDateBySymbolAndSource(canonicalSymbol, source);
    }

    public long countDistinctClosePrices(String symbol, DataSource source) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);
        return source == null ? 0L : marketHistoryRepository.countDistinctClosePriceBySymbolAndSource(canonicalSymbol, source);
    }

    @Transactional
    public long purgeHistoryForSymbol(String symbol) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);
        long deletedCount = marketHistoryRepository.deleteBySymbol(canonicalSymbol);
        if (deletedCount > 0) {
            clearHistoryCache();
        }
        log.info("Market history purge completed: symbol={}, deletedCount={}", canonicalSymbol, deletedCount);
        return deletedCount;
    }

    /**
     * Cache key generator'ı statik yapmak için - Spring'in cache key'i oluşturmasında kullanılıyor
     */
    public static String getCacheKey(String symbol, DataSource source, LocalDate startDate, LocalDate endDate) {
        return symbol + (source != null ? "|" + source : "") + "|" + startDate + "|" + endDate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void clearHistoryCacheOnStartup() {
        Cache cache = getHistoryCache();
        if (cache != null) {
            cache.clear();
            log.info("Market history cache cleared on startup: cacheName={}", MARKET_HISTORY_CACHE);
        }
    }

    public void clearHistoryCache() {
        Cache cache = getHistoryCache();
        if (cache != null) {
            cache.clear();
            log.info("Market history cache cleared manually: cacheName={}", MARKET_HISTORY_CACHE);
        }
    }

    private List<MarketHistoryRecord> deduplicate(List<MarketHistoryRecord> records) {
        Map<String, MarketHistoryRecord> uniqueRecords = new LinkedHashMap<>();
        for (MarketHistoryRecord record : records) {
            String key = historyKey(record.symbol(), record.source(), record.priceDate());
            uniqueRecords.putIfAbsent(key, record);
        }
        return List.copyOf(uniqueRecords.values());
    }

    private List<MarketHistoryRecord> filterInvalidConstantPriceSeries(DataSource source, List<MarketHistoryRecord> records) {
        if (records.isEmpty()) {
            return records;
        }

        Map<String, List<MarketHistoryRecord>> recordsBySeries = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> record.symbol() + "|" + record.source(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        List<MarketHistoryRecord> filtered = new java.util.ArrayList<>();
        for (Map.Entry<String, List<MarketHistoryRecord>> entry : recordsBySeries.entrySet()) {
            List<MarketHistoryRecord> seriesRecords = entry.getValue();
            long distinctDateCount = seriesRecords.stream()
                    .map(MarketHistoryRecord::priceDate)
                    .distinct()
                    .count();
            long distinctPriceCount = seriesRecords.stream()
                    .map(MarketHistoryRecord::closePrice)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();

            boolean invalidConstantSeries = distinctDateCount > 1 && distinctPriceCount < 2;
            if (invalidConstantSeries) {
                MarketHistoryRecord sample = seriesRecords.getFirst();
                log.warn(
                        "marketHistoryInvalid: symbol={}, source={}, reason=constant_price_series",
                        sample.symbol(),
                        sample.source()
                );
                continue;
            }

            filtered.addAll(seriesRecords);
        }

        return List.copyOf(filtered);
    }

    private String historyKey(String symbol, DataSource source, LocalDate priceDate) {
        return symbol + "|" + source + "|" + priceDate;
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

    private int insertIgnoringConflicts(List<MarketHistoryEntity> entities) {
        if (entities.isEmpty()) {
            return 0;
        }

        if (jdbcTemplate == null) {
            marketHistoryRepository.saveAll(entities);
            return entities.size();
        }

        int[] results = jdbcTemplate.batchUpdate("""
                insert into market_history
                    (symbol, display_name, instrument_type, source, price_date, close_price, currency, created_at)
                values
                    (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MarketHistoryEntity entity = entities.get(i);
                ps.setString(1, entity.getSymbol());
                ps.setString(2, entity.getDisplayName());
                ps.setString(3, entity.getInstrumentType().name());
                ps.setString(4, entity.getSource().name());
                ps.setObject(5, entity.getPriceDate());
                ps.setBigDecimal(6, entity.getClosePrice());
                ps.setString(7, entity.getCurrency());
                ps.setTimestamp(8, entity.getCreatedAt() == null ? null : Timestamp.from(entity.getCreatedAt()));
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });

        int insertedCount = 0;
        for (int result : results) {
            if (result > 0) {
                insertedCount += result;
            }
        }
        return insertedCount;
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

    private List<MarketHistoryRecord> readCachedHistory(String cacheKey) {
        Cache cache = getHistoryCache();
        if (cache == null) {
            return null;
        }

        try {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper == null) {
                return null;
            }

            Object cachedValue = wrapper.get();
            return normalizeCachedHistory(cache, cacheKey, cachedValue);
        } catch (RuntimeException ex) {
            log.warn("Market history cache read failed, bypassing cache: key={}, error={}", cacheKey, ex.getMessage(), ex);
            evictQuietly(cache, cacheKey);
            return null;
        }
    }

    private List<MarketHistoryRecord> normalizeCachedHistory(Cache cache, String cacheKey, Object cachedValue) {
        if (cachedValue == null) {
            return null;
        }

        try {
            if (cachedValue instanceof List<?> cachedList) {
                List<MarketHistoryRecord> normalized = cachedList.stream()
                        .map(this::toHistoryRecord)
                        .toList();
                log.debug("Market history cache hit: key={}, recordCount={}", cacheKey, normalized.size());
                return normalized;
            }

            throw new IllegalStateException("Unexpected market history cache payload type: " + cachedValue.getClass().getName());
        } catch (RuntimeException ex) {
            log.warn("Market history cache payload invalid, bypassing cache: key={}, error={}", cacheKey, ex.getMessage(), ex);
            evictQuietly(cache, cacheKey);
            return null;
        }
    }

    private MarketHistoryRecord toHistoryRecord(Object value) {
        if (value instanceof MarketHistoryRecord historyRecord) {
            return historyRecord;
        }

        if (value instanceof Map<?, ?> mapValue) {
            return objectMapper.convertValue(mapValue, MarketHistoryRecord.class);
        }

        return objectMapper.convertValue(value, MarketHistoryRecord.class);
    }

    private void writeCachedHistory(String cacheKey, List<MarketHistoryRecord> history) {
        if (history == null || history.isEmpty()) {
            return;
        }

        Cache cache = getHistoryCache();
        if (cache == null) {
            return;
        }

        try {
            cache.put(cacheKey, history);
        } catch (RuntimeException ex) {
            log.warn("Market history cache write failed: key={}, error={}", cacheKey, ex.getMessage(), ex);
        }
    }

    private Cache getHistoryCache() {
        return cacheManager.getCache(MARKET_HISTORY_CACHE);
    }

    private void logHistoryRead(String layer,
                                String symbol,
                                DataSource source,
                                LocalDate startDate,
                                LocalDate endDate,
                                List<MarketHistoryRecord> history) {
        LocalDate minReturnedDate = history.stream()
                .map(MarketHistoryRecord::priceDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxReturnedDate = history.stream()
                .map(MarketHistoryRecord::priceDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        long distinctPriceCount = history.stream()
                .map(MarketHistoryRecord::closePrice)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        log.info(
                "Market history read completed: layer={}, symbol={}, source={}, resolvedStartDate={}, resolvedEndDate={}, dbReturnedCount={}, minReturnedDate={}, maxReturnedDate={}, distinctPriceCount={}",
                layer,
                symbol,
                source,
                startDate,
                endDate,
                history.size(),
                minReturnedDate,
                maxReturnedDate,
                distinctPriceCount
        );
    }

    private void evictQuietly(Cache cache, String cacheKey) {
        try {
            cache.evict(cacheKey);
        } catch (RuntimeException ex) {
            log.warn("Market history cache eviction failed: key={}, error={}", cacheKey, ex.getMessage(), ex);
        }
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
