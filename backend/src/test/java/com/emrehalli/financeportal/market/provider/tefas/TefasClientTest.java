package com.emrehalli.financeportal.market.provider.tefas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.tefas.config.TefasProperties;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TefasClientTest {

    @Test
    void fetchQuotesBuildsLatestQuoteAndLatestHistoryRecord() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://www.tefas.gov.tr/api/funds/fonGnlBlgSiraliGetir?fonKodu=AFT"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"FIYAT": 10.50, "TARIH": "2025-05-06"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://www.tefas.gov.tr/api/funds/fonGecmisVerisiGetir?fonKodu=AFT&baslangicTarih=2025-04-29&bitisTarih=2025-05-06"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"FIYAT": 10.00, "TARIH": "2025-05-05"},
                            {"FIYAT": 10.50, "TARIH": "2025-05-06"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TefasClient client = client(restTemplate, registry());

        ProviderFetchResult result = client.fetch(ProviderFetchRequest.forSymbols(List.of("AFT")));

        server.verify();
        assertThat(result.quotes()).singleElement().satisfies(quote -> {
            assertThat(quote.symbol()).isEqualTo("AFT");
            assertThat(quote.source()).isEqualTo(DataSource.TEFAS);
            assertThat(quote.currency()).isEqualTo("TRY");
            assertThat(quote.price()).isEqualByComparingTo("10.50");
            assertThat(quote.changeRate()).hasToString("5.000000");
        });
        assertThat(result.historyRecords()).singleElement().satisfies(record -> {
            assertThat(record.symbol()).isEqualTo("AFT");
            assertThat(record.source()).isEqualTo(DataSource.TEFAS);
            assertThat(record.priceDate()).isEqualTo(LocalDate.of(2025, 5, 6));
        });
    }

    @Test
    void fetchHistoryBuildsRecordsForRequestedDateRange() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://www.tefas.gov.tr/api/funds/fonGecmisVerisiGetir?fonKodu=AFT&baslangicTarih=2025-05-04&bitisTarih=2025-05-05"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"FIYAT": 9.95, "TARIH": "2025-05-04"},
                            {"FIYAT": 10.00, "TARIH": "2025-05-05"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TefasClient client = client(restTemplate, registry());

        ProviderFetchResult result = client.fetch(new ProviderFetchRequest(
                DataSource.TEFAS,
                List.of("AFT"),
                java.util.Set.of(InstrumentType.FUND),
                LocalDate.of(2025, 5, 4),
                LocalDate.of(2025, 5, 5),
                Map.of()
        ));

        server.verify();
        assertThat(result.quotes()).isEmpty();
        assertThat(result.historyRecords()).hasSize(2);
        assertThat(result.historyRecords())
                .extracting(record -> record.priceDate())
                .containsExactly(LocalDate.of(2025, 5, 4), LocalDate.of(2025, 5, 5));
    }

    @Test
    void supportsOnlyFundAndTefasRequests() {
        TefasClient client = client(new RestTemplate(), registry());

        assertThat(client.supports(ProviderFetchRequest.all())).isTrue();
        assertThat(client.supports(ProviderFetchRequest.forSource(DataSource.TEFAS))).isTrue();
        assertThat(client.supports(ProviderFetchRequest.forSource(DataSource.BINANCE))).isFalse();
        assertThat(client.supports(new ProviderFetchRequest(
                null,
                List.of(),
                java.util.Set.of(InstrumentType.STOCK),
                null,
                null,
                Map.of()
        ))).isFalse();
    }

    private TefasClient client(RestTemplate restTemplate, InstrumentRegistryService registryService) {
        TefasProperties properties = new TefasProperties();
        properties.setEnabled(true);
        properties.setRateLimitPerMinute(6000);
        TefasClient client = new TefasClient(
                restTemplate,
                properties,
                registryService,
                new SymbolNormalizer(),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC)
        );
        client.initializeTokenBucket();
        return client;
    }

    private InstrumentRegistryService registry() {
        return InstrumentRegistryService.seeded(new SymbolNormalizer(), List.of(
                new InstrumentRegistryService.InstrumentDefinition(
                        "AFT",
                        "AFT Fund",
                        InstrumentType.FUND,
                        "TRY",
                        Map.of(DataSource.TEFAS, "AFT")
                )
        ));
    }
}
