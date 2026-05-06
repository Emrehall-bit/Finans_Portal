package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import com.emrehalli.financeportal.market.persistence.mapper.MarketHistoryPersistenceMapper;
import com.emrehalli.financeportal.market.persistence.repository.MarketHistoryRepository;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketHistoryServiceTest {

    @Mock
    private MarketHistoryRepository marketHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistsOnlyNewHistoryRecords() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager(),
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord existingRecord = record(LocalDate.of(2026, 4, 23));
        MarketHistoryRecord newRecord = new MarketHistoryRecord(
                "USDTRY",
                "USD / TRY",
                InstrumentType.FX,
                DataSource.EVDS,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("45.123400"),
                "TRY"
        );

        when(marketHistoryRepository.findBySourceAndSymbolInAndPriceDateBetween(
                eq(DataSource.EVDS),
                argThat(symbols -> containsExactly(symbols, "USDTRY")),
                eq(LocalDate.of(2026, 4, 23)),
                eq(LocalDate.of(2026, 4, 24))
        )).thenReturn(List.of(new MarketHistoryPersistenceMapper().toEntity(existingRecord)));

        MarketHistoryPersistenceResult result = service.persistHistory(DataSource.EVDS, List.of(existingRecord, newRecord));

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.skippedDuplicate()).isEqualTo(1);
        ArgumentCaptor<List> entitiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketHistoryRepository).saveAll(entitiesCaptor.capture());
        assertThat(entitiesCaptor.getValue()).hasSize(1);
    }

    @Test
    void skipsSaveAllWhenEverythingIsDuplicate() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager(),
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord record = record(LocalDate.of(2026, 4, 23));

        when(marketHistoryRepository.findBySourceAndSymbolInAndPriceDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of(new MarketHistoryPersistenceMapper().toEntity(record)));

        MarketHistoryPersistenceResult result = service.persistHistory(DataSource.EVDS, List.of(record));

        assertThat(result.saved()).isZero();
        assertThat(result.skippedDuplicate()).isEqualTo(1);
        verify(marketHistoryRepository, never()).saveAll(any());
    }

    @Test
    void deduplicatesInMemoryBeforeSinglePreloadQuery() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager(),
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord record = record(LocalDate.of(2026, 4, 23));

        when(marketHistoryRepository.findBySourceAndSymbolInAndPriceDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        MarketHistoryPersistenceResult result = service.persistHistory(DataSource.EVDS, List.of(record, record));

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.skippedDuplicate()).isEqualTo(1);
        verify(marketHistoryRepository).findBySourceAndSymbolInAndPriceDateBetween(
                eq(DataSource.EVDS),
                argThat(symbols -> containsExactly(symbols, "USDTRY")),
                eq(LocalDate.of(2026, 4, 23)),
                eq(LocalDate.of(2026, 4, 23))
        );
    }

    @Test
    void getHistoryUsesSourceFilterWhenProvided() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager(),
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord record = record(LocalDate.of(2026, 4, 23));

        when(marketHistoryRepository.findBySymbolAndSourceAndPriceDateBetweenOrderByPriceDateAsc(
                "USDTRY",
                DataSource.EVDS,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryPersistenceMapper().toEntity(record)));

        List<MarketHistoryRecord> history = service.getHistory(
                "usd/try",
                DataSource.EVDS,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        );

        assertThat(history).containsExactly(record);
    }

    @Test
    void getHistoryKeepsExistingBehaviorWhenSourceIsMissing() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager(),
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord record = record(LocalDate.of(2026, 4, 23));

        when(marketHistoryRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                "USDTRY",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryPersistenceMapper().toEntity(record)));

        List<MarketHistoryRecord> history = service.getHistory(
                "usd/try",
                null,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        );

        assertThat(history).containsExactly(record);
    }

    @Test
    void getHistorySupportsCanonicalBistSymbols() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager(),
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord record = new MarketHistoryRecord(
                "THYAO",
                "Turk Hava Yollari",
                InstrumentType.STOCK,
                DataSource.BIST,
                LocalDate.of(2026, 4, 23),
                new BigDecimal("320.400000"),
                "TRY"
        );

        when(marketHistoryRepository.findBySymbolAndSourceAndPriceDateBetweenOrderByPriceDateAsc(
                "THYAO",
                DataSource.BIST,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryPersistenceMapper().toEntity(record)));

        List<MarketHistoryRecord> history = service.getHistory(
                "THYAO",
                DataSource.BIST,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        );

        assertThat(history).containsExactly(record);
    }

    @Test
    void marketHistoryRecordsAreJsonSerializableForCacheStorage() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(
                new ObjectMapper().findAndRegisterModules()
        );

        byte[] payload = serializer.serialize(List.of(record(LocalDate.of(2026, 4, 24))));

        assertThat(payload).isNotNull();
        assertThat(payload.length).isPositive();
    }

    @Test
    void cacheMissReadsFromDbAndCachesResult() {
        ConcurrentMapCacheManager cacheManager = cacheManager();
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager,
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord record = record(LocalDate.of(2026, 4, 23));

        when(marketHistoryRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                "USDTRY",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryPersistenceMapper().toEntity(record)));

        List<MarketHistoryRecord> history = service.getHistory("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24));

        assertThat(history).containsExactly(record);
        assertThat(cacheManager.getCache("market_history").get(MarketHistoryService.getCacheKey("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24))).get())
                .isInstanceOf(List.class);
    }

    @Test
    void cacheHitReturnsTypedMarketHistoryRecords() {
        ConcurrentMapCacheManager cacheManager = cacheManager();
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager,
                objectMapper,
                new SymbolNormalizer()
        );
        String cacheKey = MarketHistoryService.getCacheKey("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24));
        cacheManager.getCache("market_history").put(cacheKey, List.of(record(LocalDate.of(2026, 4, 23))));

        List<MarketHistoryRecord> history = service.getHistory("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24));

        assertThat(history).containsExactly(record(LocalDate.of(2026, 4, 23)));
        verify(marketHistoryRepository, never()).findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(any(), any(), any());
    }

    @Test
    void linkedHashMapCachePayloadIsConvertedSafely() {
        ConcurrentMapCacheManager cacheManager = cacheManager();
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager,
                objectMapper,
                new SymbolNormalizer()
        );
        String cacheKey = MarketHistoryService.getCacheKey("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24));
        Cache cache = cacheManager.getCache("market_history");
        Map<String, Object> rawRecord = new LinkedHashMap<>();
        rawRecord.put("symbol", "USDTRY");
        rawRecord.put("displayName", "USD / TRY");
        rawRecord.put("instrumentType", "FX");
        rawRecord.put("source", "EVDS");
        rawRecord.put("priceDate", "2026-04-23");
        rawRecord.put("closePrice", new BigDecimal("44.813200"));
        rawRecord.put("currency", "TRY");
        cache.put(cacheKey, List.of(rawRecord));

        List<MarketHistoryRecord> history = service.getHistory("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24));

        assertThat(history).containsExactly(record(LocalDate.of(2026, 4, 23)));
    }

    @Test
    void invalidCachePayloadFallsBackToDbAndRefreshesCache() {
        ConcurrentMapCacheManager cacheManager = cacheManager();
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager,
                objectMapper,
                new SymbolNormalizer()
        );
        String cacheKey = MarketHistoryService.getCacheKey("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24));
        cacheManager.getCache("market_history").put(cacheKey, "broken-payload");
        MarketHistoryRecord dbRecord = record(LocalDate.of(2026, 4, 23));

        when(marketHistoryRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                "USDTRY",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryPersistenceMapper().toEntity(dbRecord)));

        List<MarketHistoryRecord> history = service.getHistory("USDTRY", null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24));

        assertThat(history).containsExactly(dbRecord);
        assertThat(cacheManager.getCache("market_history").get(cacheKey).get()).isInstanceOf(List.class);
    }

    @Test
    void skipsPersistingConstantPriceSeriesAsInvalidHistory() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                cacheManager(),
                objectMapper,
                new SymbolNormalizer()
        );
        MarketHistoryRecord dayOne = new MarketHistoryRecord(
                "THYAO",
                "THYAO",
                InstrumentType.STOCK,
                DataSource.BIST,
                LocalDate.of(2026, 4, 23),
                new BigDecimal("320.000000"),
                "TRY"
        );
        MarketHistoryRecord dayTwo = new MarketHistoryRecord(
                "THYAO",
                "THYAO",
                InstrumentType.STOCK,
                DataSource.BIST,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("320.000000"),
                "TRY"
        );

        MarketHistoryPersistenceResult result = service.persistHistory(DataSource.BIST, List.of(dayOne, dayTwo));

        assertThat(result.saved()).isZero();
        assertThat(result.skippedDuplicate()).isEqualTo(2);
        verify(marketHistoryRepository, never()).saveAll(any());
        verify(marketHistoryRepository, never()).findBySourceAndSymbolInAndPriceDateBetween(any(), any(), any(), any());
    }

    private static MarketHistoryRecord record(LocalDate date) {
        return new MarketHistoryRecord(
                "USDTRY",
                "USD / TRY",
                InstrumentType.FX,
                DataSource.EVDS,
                date,
                new BigDecimal("44.813200"),
                "TRY"
        );
    }

    private static boolean containsExactly(Collection<String> values, String expectedValue) {
        return values != null && values.size() == 1 && values.contains(expectedValue);
    }

    private ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager("market_history");
    }
}
