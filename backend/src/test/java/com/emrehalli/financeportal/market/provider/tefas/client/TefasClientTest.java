package com.emrehalli.financeportal.market.provider.tefas.client;

import com.emrehalli.financeportal.market.provider.tefas.config.TefasProviderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TefasClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void buildsFundAnalysisUriPerSymbol() {
        TefasClient client = new TefasClient(restTemplate, properties());
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(sampleHtml()));

        client.fetchFunds(List.of("AFT"));

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().toString())
                .isEqualTo("https://www.tefas.gov.tr/FonAnaliz.aspx?FonKod=AFT");
    }

    @Test
    void parsesFundPageIntoResponse() {
        TefasClient client = new TefasClient(restTemplate, properties());
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(sampleHtml()));

        var responses = client.fetchFunds(List.of("AFT"));

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.symbol()).isEqualTo("AFT");
            assertThat(response.displayName()).isEqualTo("AK PORTFOY ALTIN FONU");
            assertThat(response.price()).isEqualTo("12,345678");
            assertThat(response.changeRate()).isEqualTo("1,2345");
            assertThat(response.priceDate()).isEqualTo(LocalDate.of(2026, 4, 24));
        });
    }

    private TefasProviderProperties properties() {
        TefasProviderProperties properties = new TefasProviderProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://www.tefas.gov.tr");
        properties.setSymbols(List.of("AFT"));
        return properties;
    }

    private String sampleHtml() {
        return """
                <html>
                <body>
                <h2>AK PORTFOY ALTIN FONU</h2>
                <div>Son Fiyat (TL): 12,345678</div>
                <div>Günlük Getiri (%): %1,2345</div>
                <div>Fiyat Tarihi 24.04.2026</div>
                </body>
                </html>
                """;
    }
}
