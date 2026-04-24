package com.emrehalli.financeportal.market.provider.evds.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.market.provider.evds.EvdsClient;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsClientTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsUrlWithSeriesCodesAndQueryParameters() {
        EvdsClient client = new EvdsClient(restTemplate, properties(), objectMapper);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("{\"items\":[]}", org.springframework.http.HttpStatus.OK));

        client.fetchSeries(
                List.of("TP.DK.USD.S.YTL", "TP.DK.EUR.S.YTL"),
                LocalDate.of(2026, 4, 17),
                LocalDate.of(2026, 4, 23)
        );

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        assertThat(urlCaptor.getValue())
                .contains("series=TP.DK.USD.S.YTL-TP.DK.EUR.S.YTL")
                .contains("&startDate=17-04-2026")
                .doesNotContain("?startDate=")
                .contains("startDate=17-04-2026")
                .contains("endDate=23-04-2026")
                .contains("type=json");
    }

    @Test
    void usesConfiguredDefaultWindowWhenDatesAreNotProvided() {
        EvdsClient client = new EvdsClient(restTemplate, properties(), objectMapper);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("{\"items\":[]}", org.springframework.http.HttpStatus.OK));

        client.fetchSeries(List.of("TP.DK.USD.S.YTL"), null, LocalDate.of(2026, 4, 23));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        assertThat(urlCaptor.getValue())
                .contains("startDate=16-04-2026")
                .contains("endDate=23-04-2026");
    }

    @Test
    void usesConfiguredSchedulerLookbackDaysForDefaultWindow() {
        EvdsProperties properties = properties();
        EvdsProperties.History history = new EvdsProperties.History();
        history.setSchedulerLookbackDays(30);
        properties.setHistory(history);
        EvdsClient client = new EvdsClient(restTemplate, properties, objectMapper);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("{\"items\":[]}", org.springframework.http.HttpStatus.OK));

        client.fetchSeries(List.of("TP.DK.USD.S.YTL"), null, LocalDate.of(2026, 4, 23));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        assertThat(urlCaptor.getValue())
                .contains("startDate=24-03-2026")
                .contains("endDate=23-04-2026");
    }

    @Test
    void parsesJsonBodyAfterLoggingRawResponse() {
        EvdsClient client = new EvdsClient(restTemplate, properties(), objectMapper);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(
                        "{\"items\":[{\"Tarih\":\"23-04-2026\",\"TP_DK_USD_A\":\"38.12\"}]}",
                        org.springframework.http.HttpStatus.OK
                ));

        var response = client.fetchSeries(List.of("TP.DK.USD.A"), LocalDate.of(2026, 4, 23), LocalDate.of(2026, 4, 23));

        assertThat(response.items()).hasSize(1);
    }

    private EvdsProperties properties() {
        EvdsProperties properties = new EvdsProperties();
        EvdsProperties.Api api = new EvdsProperties.Api();
        api.setUrl("https://evds3.tcmb.gov.tr/service/evds");
        api.setKey("test-key");
        properties.setApi(api);
        return properties;
    }
}
