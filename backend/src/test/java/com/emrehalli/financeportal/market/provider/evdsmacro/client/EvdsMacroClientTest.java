package com.emrehalli.financeportal.market.provider.evdsmacro.client;

import com.emrehalli.financeportal.market.provider.evdsmacro.EvdsMacroClient;
import com.emrehalli.financeportal.market.provider.evdsmacro.config.EvdsMacroProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsMacroClientTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postsMacroBodyWithSeriesFormulaAndDates() {
        EvdsMacroClient client = new EvdsMacroClient(restTemplate, properties(), objectMapper);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("{\"items\":[]}", org.springframework.http.HttpStatus.OK));

        client.fetchSeries(
                java.util.List.of(
                        new EvdsMacroClient.SeriesFormulaRequest("TP.FE25.OKTG01", 1),
                        new EvdsMacroClient.SeriesFormulaRequest("TP.FE25.OKTG01", 3)
                ),
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2026, 5, 8)
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));

        assertThat(urlCaptor.getValue()).isEqualTo("https://evds3.tcmb.gov.tr/igmevdsms-dis/fe");
        assertThat(entityCaptor.getValue().getBody())
                .containsEntry("series", "TP.FE25.OKTG01-TP.FE25.OKTG01")
                .containsEntry("aggregationTypes", "avg-avg")
                .containsEntry("formulas", "1-3")
                .containsEntry("startDate", "01-01-2025")
                .containsEntry("endDate", "01-05-2026")
                .containsEntry("type", "json");
    }

    @Test
    void usesMonthlyDefaultWindowWhenDatesAreNotProvided() {
        EvdsMacroClient client = new EvdsMacroClient(restTemplate, properties(), objectMapper);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("{\"items\":[]}", org.springframework.http.HttpStatus.OK));

        client.fetchSeries("TP.FE25.OKTG01", 1, null, LocalDate.of(2026, 5, 8));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(String.class), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));

        assertThat(entityCaptor.getValue().getBody())
                .containsEntry("startDate", "01-11-2024")
                .containsEntry("endDate", "01-05-2026");
    }

    private EvdsMacroProperties properties() {
        EvdsMacroProperties properties = new EvdsMacroProperties();
        EvdsMacroProperties.Api api = new EvdsMacroProperties.Api();
        api.setBaseUrl("https://evds3.tcmb.gov.tr/igmevdsms-dis");
        api.setMacroPath("/fe");
        api.setKey("test-key");
        properties.setApi(api);
        properties.setEnabled(true);
        return properties;
    }
}
