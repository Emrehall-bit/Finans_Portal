package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsBatchExecutorTest {

    @Mock
    private EvdsClient evdsClient;

    @Test
    void fallsBackToSingleRequestsWhenBatchFails() {
        EvdsFallbackRetryService fallbackRetryService = new EvdsFallbackRetryService(evdsClient);
        EvdsBatchExecutor executor = new EvdsBatchExecutor(evdsClient, fallbackRetryService);

        when(evdsClient.fetchSeries(eq(List.of("TP.TIG08", "TP.TUKFIY2025.GENEL-1")), any(), any()))
                .thenThrow(new IllegalStateException("Series does not exist"));
        when(evdsClient.fetchSeries(eq(List.of("TP.TIG08")), any(), any()))
                .thenReturn(new EvdsResponse(List.of()));
        when(evdsClient.fetchSeries(eq(List.of("TP.TUKFIY2025.GENEL-1")), any(), any()))
                .thenThrow(new IllegalStateException("Series does not exist"));

        EvdsBatchExecutor.ExecutionResult result = executor.execute(
                List.of(
                        request("TCMBISSIZLIK", "TP.TIG08"),
                        request("TCMBTUFEAYLIK", "TP.TUKFIY2025.GENEL-1")
                ),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 7)
        );

        assertThat(result.successfulPayloads()).hasSize(1);
        assertThat(result.failedSeriesCodes()).containsExactly("TP.TUKFIY2025.GENEL-1");
        assertThat(result.fallbackRetryCount()).isEqualTo(2);
        verify(evdsClient, times(3)).fetchSeries(any(), any(), any());
    }

    @Test
    void retriesTcmbFaizAndTcmbIssizlikIndividuallyAfterBatchFailure() {
        EvdsFallbackRetryService fallbackRetryService = new EvdsFallbackRetryService(evdsClient);
        EvdsBatchExecutor executor = new EvdsBatchExecutor(evdsClient, fallbackRetryService);

        when(evdsClient.fetchSeries(eq(List.of("TP.BISPOLFAIZ.TUR", "TP.TIG08")), any(), any()))
                .thenThrow(new IllegalStateException("Series does not exist"));
        when(evdsClient.fetchSeries(eq(List.of("TP.BISPOLFAIZ.TUR")), any(), any()))
                .thenReturn(new EvdsResponse(List.of()));
        when(evdsClient.fetchSeries(eq(List.of("TP.TIG08")), any(), any()))
                .thenReturn(new EvdsResponse(List.of()));

        EvdsBatchExecutor.ExecutionResult result = executor.execute(
                List.of(
                        request("TCMBFAIZ", "TP.BISPOLFAIZ.TUR"),
                        request("TCMBISSIZLIK", "TP.TIG08")
                ),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 7)
        );

        assertThat(result.successfulPayloads()).hasSize(2);
        assertThat(result.failedSeriesCodes()).isEmpty();
        assertThat(result.fallbackRetryCount()).isEqualTo(2);
        verify(evdsClient).fetchSeries(eq(List.of("TP.BISPOLFAIZ.TUR", "TP.TIG08")), any(), any());
        verify(evdsClient).fetchSeries(eq(List.of("TP.BISPOLFAIZ.TUR")), any(), any());
        verify(evdsClient).fetchSeries(eq(List.of("TP.TIG08")), any(), any());
    }

    private EvdsRequestBuilder.EvdsSeriesRequest request(String symbol, String requestCode) {
        EvdsProperties.SeriesConfig seriesConfig = new EvdsProperties.SeriesConfig();
        seriesConfig.setSymbol(symbol);
        seriesConfig.setName(symbol);
        seriesConfig.setInstrumentType(InstrumentType.MACRO_INDICATOR);
        seriesConfig.setCurrency("TRY");
        seriesConfig.setApiCode(requestCode);
        seriesConfig.setEvdsKey(requestCode);
        return new EvdsRequestBuilder.EvdsSeriesRequest(seriesConfig, requestCode, requestCode);
    }
}
