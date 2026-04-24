package com.emrehalli.financeportal.news.provider.finnhub.client;

import com.emrehalli.financeportal.common.exception.ProviderRateLimitException;
import com.emrehalli.financeportal.news.provider.finnhub.FinnhubProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinnhubClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void throwsProviderRateLimitExceptionWhenFinnhubReturns429() {
        FinnhubClient client = new FinnhubClient(restTemplate, properties());

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                null,
                new byte[0],
                null
        ));

        assertThatThrownBy(client::fetchGeneralNews)
                .isInstanceOf(ProviderRateLimitException.class)
                .hasMessageContaining("Provider rate limited: FINNHUB");
    }

    private FinnhubProperties properties() {
        FinnhubProperties properties = new FinnhubProperties();
        FinnhubProperties.Api api = new FinnhubProperties.Api();
        api.setUrl("https://finnhub.io/api/v1");
        api.setKey("test-key");
        properties.setApi(api);
        return properties;
    }
}
