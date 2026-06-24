package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FxServiceTest {

    private MarketInstrumentRepository marketInstrumentRepository;
    private MarketPriceRepository marketPriceRepository;
    private CacheService cacheService;
    private MarketDailyChangeService dailyChangeService;
    private FxService fxService;

    @BeforeEach
    void setUp() {
        marketInstrumentRepository = mock(MarketInstrumentRepository.class);
        marketPriceRepository = mock(MarketPriceRepository.class);
        cacheService = mock(CacheService.class);
        dailyChangeService = mock(MarketDailyChangeService.class);
        fxService = new FxService(
                marketInstrumentRepository,
                marketPriceRepository,
                cacheService,
                new MarketProperties(),
                dailyChangeService
        );
    }

    @Test
    void getBySourceCalculatesChangePercentFromPreviousHistoricalClose() {
        MarketInstrument sellInstrument = instrument(1L, "TCMB:USD:SELL", SourceName.TCMB);
        MarketPrice currentSellPrice = MarketPrice.builder()
                .instrument(sellInstrument)
                .sourceName(SourceName.TCMB)
                .priceValue(new BigDecimal("40.5000"))
                .priceTimestamp(LocalDateTime.of(2026, 5, 10, 0, 0))
                .build();

        when(cacheService.getList(anyString(), eq(FxRateResponse.class))).thenReturn(List.of());
        when(marketInstrumentRepository.findAllByInstrumentTypeAndSourceNameNot(InstrumentType.FX, SourceName.INTERNAL))
                .thenReturn(List.of(sellInstrument));
        when(marketPriceRepository.findLatestPricesForInstruments(anyList()))
                .thenReturn(List.of(currentSellPrice));
        when(dailyChangeService.calculate(eq(sellInstrument), eq(new BigDecimal("40.5000")), any(LocalDateTime.class), eq(SourceName.TCMB)))
                .thenReturn(new BigDecimal("1.2500"));

        List<FxRateResponse> result = fxService.getBySource(SourceName.TCMB);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getSellRate()).isEqualByComparingTo("40.5000");
        assertThat(result.getFirst().getChangePercent()).isEqualByComparingTo("1.2500");
    }

    @Test
    void getBySourceReturnsNullChangePercentWhenDailyChangeServiceReturnsNull() {
        MarketInstrument sellInstrument = instrument(1L, "TCMB:USD:SELL", SourceName.TCMB);
        MarketPrice currentSellPrice = MarketPrice.builder()
                .instrument(sellInstrument)
                .sourceName(SourceName.TCMB)
                .priceValue(new BigDecimal("40.5000"))
                .priceTimestamp(LocalDateTime.of(2026, 5, 10, 0, 0))
                .build();

        when(cacheService.getList(anyString(), eq(FxRateResponse.class))).thenReturn(List.of());
        when(marketInstrumentRepository.findAllByInstrumentTypeAndSourceNameNot(InstrumentType.FX, SourceName.INTERNAL))
                .thenReturn(List.of(sellInstrument));
        when(marketPriceRepository.findLatestPricesForInstruments(anyList()))
                .thenReturn(List.of(currentSellPrice));
        when(dailyChangeService.calculate(any(), any(), any(), any())).thenReturn(null);

        List<FxRateResponse> result = fxService.getBySource(SourceName.TCMB);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getChangePercent()).isNull();
    }

    @Test
    void getBySourceAndCodeReturnsMatchingSourceOnly() {
        MarketInstrument tcmbSellInstrument = instrument(1L, "TCMB:AUD:SELL", SourceName.TCMB);
        MarketInstrument akbankSellInstrument = instrument(2L, "AKBANK:AUD:SELL", SourceName.AKBANK);

        MarketPrice tcmbSellPrice = MarketPrice.builder()
                .instrument(tcmbSellInstrument)
                .sourceName(SourceName.TCMB)
                .priceValue(new BigDecimal("25.5000"))
                .priceTimestamp(LocalDateTime.of(2026, 5, 10, 10, 0))
                .build();

        when(cacheService.getList(anyString(), eq(FxRateResponse.class))).thenReturn(List.of());
        when(marketInstrumentRepository.findAllByInstrumentTypeAndSourceNameNot(InstrumentType.FX, SourceName.INTERNAL))
                .thenReturn(List.of(tcmbSellInstrument, akbankSellInstrument));
        when(marketPriceRepository.findLatestPricesForInstruments(anyList()))
                .thenReturn(List.of(tcmbSellPrice));
        when(dailyChangeService.calculate(any(), any(), any(), any())).thenReturn(null);

        Optional<FxRateResponse> result = fxService.getBySourceAndCode(SourceName.TCMB, "AUD");

        assertThat(result).isPresent();
        assertThat(result.get().getSource()).isEqualTo("TCMB");
        assertThat(result.get().getCode()).isEqualTo("AUD");
        assertThat(result.get().getSellRate()).isEqualByComparingTo("25.5000");
    }

    private MarketInstrument instrument(Long id, String code, SourceName source) {
        return MarketInstrument.builder()
                .id(id)
                .instrumentCode(code)
                .instrumentName(code)
                .instrumentType(InstrumentType.FX)
                .sourceName(source)
                .build();
    }
}
