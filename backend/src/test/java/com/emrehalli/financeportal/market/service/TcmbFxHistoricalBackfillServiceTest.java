package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbHistoricalFxProvider;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbHistoricalFxValue;
import com.emrehalli.financeportal.market.provider.fx.tcmb.mapper.TcmbHistoricalFxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TcmbFxHistoricalBackfillServiceTest {

    private TcmbHistoricalFxProvider historicalFxProvider;
    private TcmbHistoricalFxMapper historicalFxMapper;
    private MarketInstrumentRepository instrumentRepository;
    private MarketPriceHistoryRepository priceHistoryRepository;
    private TcmbFxHistoricalBackfillService service;

    @BeforeEach
    void setUp() {
        historicalFxProvider = mock(TcmbHistoricalFxProvider.class);
        historicalFxMapper = mock(TcmbHistoricalFxMapper.class);
        instrumentRepository = mock(MarketInstrumentRepository.class);
        priceHistoryRepository = mock(MarketPriceHistoryRepository.class);
        service = new TcmbFxHistoricalBackfillService(
                historicalFxProvider,
                historicalFxMapper,
                instrumentRepository,
                priceHistoryRepository,
                marketProperties()
        );
    }

    @Test
    void backfillUsesConfiguredStartDateWhenNoHistoryExists() {
        MarketInstrument buyInstrument = instrument("TCMB:USD:BUY");
        MarketInstrument sellInstrument = instrument("TCMB:USD:SELL");

        when(instrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:BUY")).thenReturn(Optional.of(buyInstrument));
        when(instrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:SELL")).thenReturn(Optional.of(sellInstrument));
        when(priceHistoryRepository.findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                eq(buyInstrument), eq(IntervalType.ONE_DAY), eq(SourceName.TCMB)
        )).thenReturn(Optional.empty());
        when(priceHistoryRepository.findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                eq(sellInstrument), eq(IntervalType.ONE_DAY), eq(SourceName.TCMB)
        )).thenReturn(Optional.empty());
        when(historicalFxProvider.fetchHistoricalChunked(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2024, 1, 10))
        )).thenReturn(List.of());
        when(historicalFxMapper.mapRows(eq(List.of()), any())).thenReturn(List.of());

        service.backfill();

        verify(historicalFxProvider).fetchHistoricalChunked(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2024, 1, 10))
        );
    }

    @Test
    void backfillContinuesFromDayAfterLastRecord() {
        MarketInstrument buyInstrument = instrument("TCMB:USD:BUY");
        MarketInstrument sellInstrument = instrument("TCMB:USD:SELL");
        MarketPriceHistory buyHistory = MarketPriceHistory.builder()
                .priceTimestamp(LocalDateTime.of(2024, 1, 5, 0, 0))
                .build();
        MarketPriceHistory sellHistory = MarketPriceHistory.builder()
                .priceTimestamp(LocalDateTime.of(2024, 1, 5, 0, 0))
                .build();

        when(instrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:BUY")).thenReturn(Optional.of(buyInstrument));
        when(instrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:SELL")).thenReturn(Optional.of(sellInstrument));
        when(priceHistoryRepository.findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                eq(buyInstrument), eq(IntervalType.ONE_DAY), eq(SourceName.TCMB)
        )).thenReturn(Optional.of(buyHistory));
        when(priceHistoryRepository.findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                eq(sellInstrument), eq(IntervalType.ONE_DAY), eq(SourceName.TCMB)
        )).thenReturn(Optional.of(sellHistory));
        when(historicalFxProvider.fetchHistoricalChunked(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2024, 1, 6)),
                eq(LocalDate.of(2024, 1, 10))
        )).thenReturn(List.of());
        when(historicalFxMapper.mapRows(eq(List.of()), any())).thenReturn(List.of());

        service.backfill();

        verify(historicalFxProvider).fetchHistoricalChunked(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2024, 1, 6)),
                eq(LocalDate.of(2024, 1, 10))
        );
    }

    @Test
    void backfillSkipsSaveWhenDuplicateExists() {
        MarketInstrument buyInstrument = instrument("TCMB:USD:BUY");

        when(instrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:BUY")).thenReturn(Optional.of(buyInstrument));
        when(instrumentRepository.findByInstrumentCodeIgnoreCase("TCMB:USD:SELL")).thenReturn(Optional.empty());
        when(priceHistoryRepository.findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                eq(buyInstrument), eq(IntervalType.ONE_DAY), eq(SourceName.TCMB)
        )).thenReturn(Optional.empty());
        when(historicalFxProvider.fetchHistoricalChunked(
                eq(List.of("TP.DK.USD.A.YTL")),
                eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2024, 1, 10))
        )).thenReturn(List.of(Map.of("Tarih", "01-01-2024", "TP_DK_USD_A_YTL", "29.5000")));
        when(historicalFxMapper.mapRows(any(), any())).thenReturn(List.of(
                new TcmbHistoricalFxValue(
                        "TCMB:USD:BUY",
                        "TP.DK.USD.A.YTL",
                        LocalDate.of(2024, 1, 1),
                        new BigDecimal("29.5000")
                )
        ));
        when(priceHistoryRepository.existsByInstrumentAndIntervalTypeAndPriceTimestamp(
                eq(buyInstrument),
                eq(IntervalType.ONE_DAY),
                eq(LocalDateTime.of(2024, 1, 1, 0, 0))
        )).thenReturn(true);

        service.backfill();

        verify(priceHistoryRepository, never()).save(any(MarketPriceHistory.class));
    }

    private MarketInstrument instrument(String code) {
        return MarketInstrument.builder()
                .id(1L)
                .instrumentCode(code)
                .instrumentName(code)
                .instrumentType(InstrumentType.FX)
                .sourceName(SourceName.TCMB)
                .build();
    }

    private MarketProperties marketProperties() {
        MarketProperties properties = new MarketProperties();
        properties.getProviders().getTcmb().setStartDate("01-01-2024");
        properties.getProviders().getTcmb().setEndDate("10-01-2024");
        return properties;
    }
}
