package com.emrehalli.financeportal.market.provider.fx.tcmb.client;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TcmbEvdsClientTest {

    private MockRestServiceServer server;
    private TcmbEvdsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TcmbEvdsClient(builder.build(), marketProperties());
    }

    @Test
    void fetchParsesGenericItemsResponse() {
        server.expect(requestTo("https://evds3.tcmb.gov.tr/igmevdsms-dis/series=TP.DK.USD.A.YTL&startDate=01-01-2024&endDate=10-01-2024&type=json&frequency=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("key", "test-local-key"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "Tarih": "01-01-2024",
                              "TP_DK_USD_A_YTL": "29.5000"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TcmbEvdsResponse response = client.fetch(
                "TP.DK.USD.A.YTL",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 10)
        );

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst())
                .containsEntry("Tarih", "01-01-2024")
                .containsEntry("TP_DK_USD_A_YTL", "29.5000");
    }

    @Test
    void fetchSupportsMultiSeriesUrl() {
        server.expect(requestTo("https://evds3.tcmb.gov.tr/igmevdsms-dis/series=TP.DK.USD.A.YTL-TP.DK.USD.S.YTL&startDate=01-01-2024&endDate=10-01-2024&type=json&frequency=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("key", "test-local-key"))
                .andRespond(withSuccess("""
                        {
                          "items": []
                        }
                        """, MediaType.APPLICATION_JSON));

        TcmbEvdsResponse response = client.fetch(
                java.util.List.of("TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL"),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 10)
        );

        assertThat(response.getItems()).isEmpty();
    }

    private MarketProperties marketProperties() {
        MarketProperties properties = new MarketProperties();
        properties.getProviders().getTcmb().setBaseUrl("https://evds3.tcmb.gov.tr/igmevdsms-dis");
        properties.getProviders().getTcmb().setApiKey("test-local-key");
        properties.getProviders().getTcmb().setStartDate("01-01-2000");
        properties.getProviders().getTcmb().setEndDate("01-01-2999");
        return properties;
    }
}




