package com.emrehalli.financeportal.market.provider.fx.tcmb;

import com.emrehalli.financeportal.market.provider.fx.tcmb.client.TcmbEvdsClient;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TcmbHistoricalFxProviderTest {

    @Test
    void fetchHistoricalChunkedSplitsLongRangeIntoMultipleRequests() {
        TcmbEvdsClient client = mock(TcmbEvdsClient.class);
        TcmbHistoricalFxProvider provider = new TcmbHistoricalFxProvider(client);

        TcmbEvdsResponse first = responseWithDate("01-01-2020");
        TcmbEvdsResponse second = responseWithDate("19-06-2022");

        when(client.fetch(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2020, 1, 1)),
                eq(LocalDate.of(2022, 6, 18))
        )).thenReturn(first);

        when(client.fetch(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2022, 6, 19)),
                eq(LocalDate.of(2022, 12, 31))
        )).thenReturn(second);

        List<Map<String, Object>> items = provider.fetchHistoricalChunked(
                List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL"),
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2022, 12, 31)
        );

        assertThat(items).hasSize(2);
        verify(client, times(1)).fetch(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2020, 1, 1)),
                eq(LocalDate.of(2022, 6, 18))
        );
        verify(client, times(1)).fetch(
                eq(List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL")),
                eq(LocalDate.of(2022, 6, 19)),
                eq(LocalDate.of(2022, 12, 31))
        );
    }

    private static TcmbEvdsResponse responseWithDate(String date) {
        TcmbEvdsResponse response = new TcmbEvdsResponse();
        response.setItems(List.of(Map.of("Tarih", date)));
        return response;
    }
}
