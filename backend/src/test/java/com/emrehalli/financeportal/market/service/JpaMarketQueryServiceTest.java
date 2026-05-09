package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaMarketQueryServiceTest {

    private MarketInstrumentRepository marketInstrumentRepository;
    private MarketPriceRepository marketPriceRepository;
    private MarketPriceHistoryRepository marketPriceHistoryRepository;
    private JpaMarketQueryService service;

    @BeforeEach
    void setUp() {
        marketInstrumentRepository = mock(MarketInstrumentRepository.class);
        marketPriceRepository = mock(MarketPriceRepository.class);
        marketPriceHistoryRepository = mock(MarketPriceHistoryRepository.class);
        service = new JpaMarketQueryService(
                marketInstrumentRepository,
                marketPriceRepository,
                marketPriceHistoryRepository
        );
    }

    @Test
    void getHistoryReadsFromMarketPriceHistoryWithDefaultOneDayInterval() {
        MarketInstrument instrument = instrument("TCMB:USD:SELL");
        MarketPriceHistory history = MarketPriceHistory.builder()
                .instrument(instrument)
                .closePrice(new BigDecimal("39.1200"))
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.TCMB)
                .priceTimestamp(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:SELL"))
                .thenReturn(Optional.of(instrument));
        when(marketPriceHistoryRepository.findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                eq(instrument),
                eq(IntervalType.ONE_DAY),
                eq(LocalDate.of(2024, 1, 1).atStartOfDay()),
                eq(LocalDate.of(2024, 1, 10).plusDays(1).atStartOfDay().minusNanos(1))
        )).thenReturn(List.of(history));

        List<MarketQueryService.HistoricalPrice> result = service.getHistory(
                "TCMB:USD:SELL",
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 10)
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().symbol()).isEqualTo("TCMB:USD:SELL");
        assertThat(result.getFirst().priceDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getFirst().closePrice()).isEqualByComparingTo("39.1200");
    }

    @Test
    void getHistoryAppliesSourceFilterWhenProvided() {
        MarketInstrument instrument = instrument("TCMB:USD:SELL");

        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:SELL"))
                .thenReturn(Optional.of(instrument));
        when(marketPriceHistoryRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                eq(instrument),
                eq(IntervalType.ONE_DAY),
                eq(SourceName.TCMB),
                eq(LocalDate.of(2024, 1, 1).atStartOfDay()),
                eq(LocalDate.of(2024, 1, 10).plusDays(1).atStartOfDay().minusNanos(1))
        )).thenReturn(List.of());

        service.getHistory(
                "TCMB:USD:SELL",
                SourceName.TCMB,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 10)
        );

        verify(marketPriceHistoryRepository)
                .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        eq(instrument),
                        eq(IntervalType.ONE_DAY),
                        eq(SourceName.TCMB),
                        eq(LocalDate.of(2024, 1, 1).atStartOfDay()),
                        eq(LocalDate.of(2024, 1, 10).plusDays(1).atStartOfDay().minusNanos(1))
                );
    }

    private MarketInstrument instrument(String code) {
        return MarketInstrument.builder()
                .instrumentCode(code)
                .instrumentName(code)
                .instrumentType(InstrumentType.FX)
                .sourceName(SourceName.TCMB)
                .build();
    }
}
