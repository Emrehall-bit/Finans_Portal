package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbFxSeriesDefinitions;
import com.emrehalli.financeportal.market.provider.fx.tcmb.client.TcmbEvdsClient;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbHistoricalFxValue;
import com.emrehalli.financeportal.market.provider.fx.tcmb.mapper.TcmbHistoricalFxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TcmbFxCurrentPriceSyncServiceTest {

    private TcmbEvdsClient tcmbEvdsClient;
    private TcmbHistoricalFxMapper tcmbHistoricalFxMapper;
    private FxService fxService;
    private TcmbFxCurrentPriceSyncService service;

    @BeforeEach
    void setUp() {
        tcmbEvdsClient = mock(TcmbEvdsClient.class);
        tcmbHistoricalFxMapper = mock(TcmbHistoricalFxMapper.class);
        fxService = mock(FxService.class);
        service = new TcmbFxCurrentPriceSyncService(tcmbEvdsClient, tcmbHistoricalFxMapper, fxService);
    }

    @Test
    void syncCurrentPricesUsesLastSevenDaysAndSavesLatestSellRates() {
        TcmbEvdsResponse response = new TcmbEvdsResponse();
        response.setItems(List.of());

        when(tcmbEvdsClient.fetch(any(List.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);
        when(tcmbHistoricalFxMapper.mapRows(eq(List.of()), eq(TcmbFxSeriesDefinitions.CURRENT_SYNC_DEFINITIONS)))
                .thenReturn(List.of(
                        new TcmbHistoricalFxValue("TCMB:USD:BUY", "TP.DK.USD.A.YTL", LocalDate.of(2024, 1, 7), new BigDecimal("32.1500")),
                        new TcmbHistoricalFxValue("TCMB:USD:SELL", "TP.DK.USD.S.YTL", LocalDate.of(2024, 1, 5), new BigDecimal("32.1000")),
                        new TcmbHistoricalFxValue("TCMB:USD:SELL", "TP.DK.USD.S.YTL", LocalDate.of(2024, 1, 7), new BigDecimal("32.4500")),
                        new TcmbHistoricalFxValue("TCMB:EUR:BUY", "TP.DK.EUR.A.YTL", LocalDate.of(2024, 1, 6), new BigDecimal("34.1500")),
                        new TcmbHistoricalFxValue("TCMB:EUR:SELL", "TP.DK.EUR.S.YTL", LocalDate.of(2024, 1, 6), new BigDecimal("34.5500"))
                ));

        service.syncCurrentPrices();

        ArgumentCaptor<List<String>> seriesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(tcmbEvdsClient).fetch(seriesCaptor.capture(), startCaptor.capture(), endCaptor.capture());

        assertThat(seriesCaptor.getValue()).containsExactlyElementsOf(
                TcmbFxSeriesDefinitions.CURRENT_SYNC_DEFINITIONS.stream()
                        .map(definition -> definition.seriesCode())
                        .toList()
        );
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.now());
        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(6));

        ArgumentCaptor<List<FxRateDto>> ratesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fxService).saveAll(ratesCaptor.capture());

        List<FxRateDto> rates = ratesCaptor.getValue();
        assertThat(rates).hasSize(2);

        FxRateDto usdRate = rates.stream()
                .filter(rate -> "USD".equals(rate.getCurrencyCode()))
                .findFirst()
                .orElseThrow();
        assertThat(usdRate.getSourceName()).isEqualTo(SourceName.TCMB);
        assertThat(usdRate.getBuyPrice()).isEqualByComparingTo("32.1500");
        assertThat(usdRate.getSellPrice()).isEqualByComparingTo("32.4500");
        assertThat(usdRate.getDataTimestamp()).isEqualTo(LocalDateTime.of(2024, 1, 7, 0, 0));

        FxRateDto eurRate = rates.stream()
                .filter(rate -> "EUR".equals(rate.getCurrencyCode()))
                .findFirst()
                .orElseThrow();
        assertThat(eurRate.getSourceName()).isEqualTo(SourceName.TCMB);
        assertThat(eurRate.getBuyPrice()).isEqualByComparingTo("34.1500");
        assertThat(eurRate.getSellPrice()).isEqualByComparingTo("34.5500");
        assertThat(eurRate.getDataTimestamp()).isEqualTo(LocalDateTime.of(2024, 1, 6, 0, 0));
    }
}




