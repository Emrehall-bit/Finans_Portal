package com.emrehalli.financeportal.market.provider.yahoo;

import com.emrehalli.financeportal.market.exception.DataProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YahooFinanceSessionServiceTest {

    private static final String BOOTSTRAP_URL = "https://finance.yahoo.com/quote/AAPL";
    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final String API_URL = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=AAPL";
    private static final String API_URL_WITH_CRUMB = API_URL + "&crumb=abc123";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final YahooFinanceSessionService service = new YahooFinanceSessionService(restTemplate);

    @Test
    void exchangeWithSessionFetchesCookieAndCrumbOnceThenReusesThem() {
        when(restTemplate.exchange(eq(BOOTSTRAP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("ok", "A1=session-cookie"));
        when(restTemplate.exchange(eq(CRUMB_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("abc123", "B=session-cookie"));
        when(restTemplate.exchange(eq(API_URL_WITH_CRUMB), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"quoteResponse\":{\"result\":[]}}"));

        ResponseEntity<String> first = service.exchangeWithSession(API_URL);
        ResponseEntity<String> second = service.exchangeWithSession(API_URL);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(restTemplate, times(1))
                .exchange(eq(BOOTSTRAP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, times(1))
                .exchange(eq(CRUMB_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, times(2))
                .exchange(eq(API_URL_WITH_CRUMB), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void exchangeWithSessionRefreshesSessionAndRetriesOnceWhenForbidden() {
        when(restTemplate.exchange(eq(BOOTSTRAP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("ok", "A1=first-cookie"))
                .thenReturn(response("ok", "A1=second-cookie"));
        when(restTemplate.exchange(eq(CRUMB_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("abc123", "B=first-cookie"))
                .thenReturn(response("abc123", "B=second-cookie"));
        when(restTemplate.exchange(eq(API_URL_WITH_CRUMB), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(httpError(HttpStatus.FORBIDDEN))
                .thenReturn(ResponseEntity.ok("{\"quoteResponse\":{\"result\":[]}}"));

        ResponseEntity<String> response = service.exchangeWithSession(API_URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(restTemplate, times(2))
                .exchange(eq(BOOTSTRAP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, times(2))
                .exchange(eq(CRUMB_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, times(2))
                .exchange(eq(API_URL_WITH_CRUMB), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void exchangeWithSessionRejectsHtmlCrumb() {
        when(restTemplate.exchange(eq(BOOTSTRAP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("ok", "A1=session-cookie"));
        when(restTemplate.exchange(eq(CRUMB_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("<html>error</html>", "B=session-cookie"));

        assertThatThrownBy(() -> service.exchangeWithSession(API_URL))
                .isInstanceOf(DataProviderException.class)
                .hasMessageContaining("invalid crumb");
    }

    @Test
    void exchangeWithSessionDoesNotRefreshOnRateLimit() {
        when(restTemplate.exchange(eq(BOOTSTRAP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("ok", "A1=session-cookie"));
        when(restTemplate.exchange(eq(CRUMB_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response("abc123", "B=session-cookie"));
        when(restTemplate.exchange(eq(API_URL_WITH_CRUMB), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(httpError(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.exchangeWithSession(API_URL))
                .isInstanceOf(DataProviderException.class)
                .hasMessageContaining("rate limit");

        verify(restTemplate, times(1))
                .exchange(eq(BOOTSTRAP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, times(1))
                .exchange(eq(CRUMB_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, times(1))
                .exchange(eq(API_URL_WITH_CRUMB), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    private ResponseEntity<String> response(String body, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie + "; Path=/; Domain=.yahoo.com");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private HttpClientErrorException httpError(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }
}
