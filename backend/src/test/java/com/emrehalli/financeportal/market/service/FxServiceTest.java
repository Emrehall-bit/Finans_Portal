package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FxServiceTest {

    private MarketInstrumentRepository marketInstrumentRepository;
    private MarketPriceRepository marketPriceRepository;
    private MarketPriceHistoryRepository marketPriceHistoryRepository;
    private CacheService cacheService;
    private FxService fxService;

    @BeforeEach
    void setUp() {
        marketInstrumentRepository = mock(MarketInstrumentRepository.class);
        marketPriceRepository = mock(MarketPriceRepository.class);
        marketPriceHistoryRepository = mock(MarketPriceHistoryRepository.class);
        cacheService = mock(CacheService.class);
        fxService = new FxService(
                marketInstrumentRepository,
                marketPriceRepository,
                marketPriceHistoryRepository,
                cacheService,
                new MarketProperties()
        );
    }

    @Test
    void getBySourceCalculatesChangePercentFromPreviousHistoricalClose() {
        MarketInstrument sellInstrument = instrument("TCMB:USD:SELL");
        MarketPrice currentSellPrice = MarketPrice.builder()
                .instrument(sellInstrument)
                .sourceName(SourceName.TCMB)
                .priceValue(new BigDecimal("40.5000"))
                .priceTimestamp(LocalDateTime.of(2026, 5, 10, 0, 0))
                .build();
        MarketPriceHistory previousClose = MarketPriceHistory.builder()
                .instrument(sellInstrument)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.TCMB)
                .closePrice(new BigDecimal("40.0000"))
                .priceTimestamp(LocalDate.of(2026, 5, 9).atStartOfDay().toInstant(ZoneOffset.UTC))
                .build();

        when(cacheService.getList("fx:source:TCMB", FxRateResponse.class)).thenReturn(List.of());
        when(cacheService.get("fx:TCMB:USD", FxRateResponse.class)).thenReturn(Optional.empty());
        when(marketInstrumentRepository.findAll()).thenReturn(List.of(sellInstrument));
        when(marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(eq(sellInstrument)))
                .thenReturn(Optional.of(currentSellPrice));
        when(marketPriceHistoryRepository.findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                eq(sellInstrument),
                eq(IntervalType.ONE_DAY),
                eq(SourceName.TCMB),
                any(Instant.class)
        )).thenReturn(Optional.of(previousClose));

        List<FxRateResponse> result = fxService.getBySource(SourceName.TCMB);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getSellRate()).isEqualByComparingTo("40.5000");
        assertThat(result.getFirst().getChangePercent()).isEqualByComparingTo("1.25000000");
    }

    @Test
    void getBySourceReturnsNullChangePercentWhenPreviousCloseMissing() {
        MarketInstrument sellInstrument = instrument("TCMB:USD:SELL");
        MarketPrice currentSellPrice = MarketPrice.builder()
                .instrument(sellInstrument)
                .sourceName(SourceName.TCMB)
                .priceValue(new BigDecimal("40.5000"))
                .priceTimestamp(LocalDateTime.of(2026, 5, 10, 0, 0))
                .build();

        when(cacheService.getList("fx:source:TCMB", FxRateResponse.class)).thenReturn(List.of());
        when(cacheService.get("fx:TCMB:USD", FxRateResponse.class)).thenReturn(Optional.empty());
        when(marketInstrumentRepository.findAll()).thenReturn(List.of(sellInstrument));
        when(marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(eq(sellInstrument)))
                .thenReturn(Optional.of(currentSellPrice));
        when(marketPriceHistoryRepository.findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                eq(sellInstrument),
                eq(IntervalType.ONE_DAY),
                eq(SourceName.TCMB),
                any(Instant.class)
        )).thenReturn(Optional.empty());

        List<FxRateResponse> result = fxService.getBySource(SourceName.TCMB);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getChangePercent()).isNull();
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



