package com.emrehalli.financeportal.news.provider.finnhub.client;

import com.emrehalli.financeportal.news.provider.finnhub.dto.FinnhubNewsResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@Component
public class FinnhubClient {

    private static final Logger logger = LogManager.getLogger(FinnhubClient.class);

    private final RestTemplate restTemplate;

    @Value("${finnhub.api.key}")
    private String apiKey;

    @Value("${finnhub.api.url}")
    private String baseUrl;

    @Value("${finnhub.sync.default-days-back:1}")
    private int defaultDaysBack;

    public FinnhubClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<FinnhubNewsResponse> fetchGeneralNews() {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/news")
                .queryParam("category", "general")
                .queryParam("token", apiKey)
                .toUriString();
        return execute(url, "general-news");
    }

    public List<FinnhubNewsResponse> fetchCompanyNews(String symbol) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(defaultDaysBack);

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/company-news")
                .queryParam("symbol", symbol)
                .queryParam("from", fromDate)
                .queryParam("to", toDate)
                .queryParam("token", apiKey)
                .toUriString();
        return execute(url, "company-news");
    }

    private List<FinnhubNewsResponse> execute(String url, String endpoint) {
        try {
            logger.info("Sending Finnhub request to endpoint: {}", endpoint);
            logger.debug("Finnhub request URL: {}", url);

            ResponseEntity<List<FinnhubNewsResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );

            List<FinnhubNewsResponse> body = response.getBody();
            int count = body == null ? 0 : body.size();
            logger.info("Finnhub response received. Endpoint: {}, count: {}", endpoint, count);

            return body == null ? List.of() : body;
        } catch (HttpClientErrorException.TooManyRequests e) {
            logger.warn("Finnhub rate limit exceeded on endpoint: {}", endpoint);
            return List.of();
        } catch (HttpClientErrorException e) {
            logger.error("Finnhub client error. Endpoint: {}, status: {}, body: {}",
                    endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (RestClientException e) {
            logger.error("Finnhub communication error on endpoint: {}", endpoint, e);
            return List.of();
        } catch (Exception e) {
            logger.error("Unexpected Finnhub error on endpoint: {}", endpoint, e);
            return List.of();
        }
    }
}
