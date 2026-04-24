package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketHistoryBackfillServiceTest {

    @Mock
    private ProviderOrchestrationService providerOrchestrationService;

    @Mock
    private MarketHistoryService marketHistoryService;

    @Test
    void usesEvdsDefaultBackfillDaysWhenRequestDoesNotProvideOne() {
        EvdsProperties evdsProperties = new EvdsProperties();
        EvdsProperties.History history = new EvdsProperties.History();
        history.setBackfillDefaultDays(365);
        evdsProperties.setHistory(history);
        MarketHistoryBackfillService service = new MarketHistoryBackfillService(
                providerOrchestrationService,
                marketHistoryService,
                evdsProperties
        );
        when(providerOrchestrationService.fetchQuoteResults(any()))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.EVDS, List.of())));
        when(marketHistoryService.persistHistory(any(), any()))
                .thenReturn(new MarketHistoryPersistenceResult(DataSource.EVDS, 0, 0, 0));

        List<MarketHistoryPersistenceResult> results = service.backfill(DataSource.EVDS, null);

        assertThat(service.resolveLookbackDays(DataSource.EVDS, null)).isEqualTo(365);
        assertThat(results).singleElement().satisfies(result -> assertThat(result.source()).isEqualTo(DataSource.EVDS));
        verify(providerOrchestrationService).fetchQuoteResults(any());
    }

    @Test
    void usesDefaultBackfillDaysForBinanceWhenRequestDoesNotProvideOne() {
        MarketHistoryBackfillService service = new MarketHistoryBackfillService(
                providerOrchestrationService,
                marketHistoryService,
                new EvdsProperties()
        );
        when(providerOrchestrationService.fetchQuoteResults(any()))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.BINANCE, List.of())));
        when(marketHistoryService.persistHistory(any(), any()))
                .thenReturn(new MarketHistoryPersistenceResult(DataSource.BINANCE, 0, 0, 0));

        List<MarketHistoryPersistenceResult> results = service.backfill(DataSource.BINANCE, null);

        assertThat(service.resolveLookbackDays(DataSource.BINANCE, null)).isEqualTo(365);
        assertThat(results).singleElement().satisfies(result -> assertThat(result.source()).isEqualTo(DataSource.BINANCE));
        verify(providerOrchestrationService).fetchQuoteResults(any());
    }
}
