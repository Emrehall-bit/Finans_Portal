package com.emrehalli.financeportal.market.provider.fx;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AkbankFxProviderTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AkbankFxProvider provider = new AkbankFxProvider(restTemplate, new ObjectMapper());

    @Test
    void fetchMapsAkbankRatesToFxRateDtos() {
        String payload = """
                {
                  "d": {
                    "Data": {
                      "DovizKurlari": [
                        {
                          "AlfaKod": "USD",
                          "DovizAlis": "44.411",
                          "DovizSatis": "47.111",
                          "EfektifAlis": "44.411",
                          "EfektifSatis": "47.111",
                          "KurDegisim": "0,0010",
                          "KurYon": "+",
                          "KurGuncellemeZamani": "01.06.2026 21:46:28"
                        },
                        {
                          "AlfaKod": "EUR",
                          "DovizAlis": "51,6681",
                          "DovizSatis": "54,814",
                          "KurGuncellemeZamani": "01.06.2026 21:46:28"
                        }
                      ]
                    }
                  }
                }
                """;
        when(restTemplate.postForEntity(
                eq("https://www.akbank.com/_layouts/15/Akbank/CalcTools/Ajax.aspx/GetDovizKurlari"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(payload));

        List<FxRateDto> rates = provider.fetch();

        assertThat(rates).hasSize(2);
        assertThat(rates.getFirst().getSourceName()).isEqualTo(SourceName.AKBANK);
        assertThat(rates.getFirst().getCurrencyCode()).isEqualTo("USD");
        assertThat(rates.getFirst().getBuyPrice()).isEqualByComparingTo(new BigDecimal("44.411"));
        assertThat(rates.getFirst().getSellPrice()).isEqualByComparingTo(new BigDecimal("47.111"));
        assertThat(rates.getFirst().getDataTimestamp()).isEqualTo(LocalDateTime.of(2026, 6, 1, 21, 46, 28));
        assertThat(rates.get(1).getCurrencyCode()).isEqualTo("EUR");
        assertThat(rates.get(1).getBuyPrice()).isEqualByComparingTo(new BigDecimal("51.6681"));
        assertThat(rates.get(1).getSellPrice()).isEqualByComparingTo(new BigDecimal("54.814"));
    }

    @Test
    void fetchReturnsEmptyListWhenProviderFails() {
        when(restTemplate.postForEntity(
                eq("https://www.akbank.com/_layouts/15/Akbank/CalcTools/Ajax.aspx/GetDovizKurlari"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        List<FxRateDto> rates = provider.fetch();

        assertThat(rates).isEmpty();
    }
}
