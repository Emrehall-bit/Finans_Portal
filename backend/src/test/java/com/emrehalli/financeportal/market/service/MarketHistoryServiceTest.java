package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.mapper.MarketHistoryPersistenceMapper;
import com.emrehalli.financeportal.market.persistence.repository.MarketHistoryRepository;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

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

    @Test
    void persistsOnlyNewHistoryRecords() {
        MarketHistoryService service = new MarketHistoryService(
                marketHistoryRepository,
                new MarketHistoryPersistenceMapper(),
                new SymbolNormalizer()
        );
        MarketHistoryRecord existingRecord = record(LocalDate.of(2026, 4, 23));
        MarketHistoryRecord newRecord = record(LocalDate.of(2026, 4, 24));

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
}
