package com.emrehalli.financeportal.market.provider.fx.tcmb.client;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

@Component
@Slf4j
@RequiredArgsConstructor
public class TcmbEvdsClient {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestClient restClient;
    private final MarketProperties marketProperties;

    public TcmbEvdsResponse fetch(String seriesCode, LocalDate startDate, LocalDate endDate) {
        return fetch(List.of(seriesCode), startDate, endDate);
    }

    public TcmbEvdsResponse fetch(List<String> seriesCodes, LocalDate startDate, LocalDate endDate) {
        validateRequest(seriesCodes, startDate, endDate);

        String apiKey = marketProperties.getProviders().getTcmb().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new DataProviderException("TCMB API key is missing");
        }

        String joinedSeriesCodes = joinSeriesCodes(seriesCodes);
        String url = buildUrl(joinedSeriesCodes, startDate, endDate);

        try {
            TcmbEvdsResponse response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header("key", apiKey)
                    .retrieve()
                    .body(TcmbEvdsResponse.class);

            if (response == null) {
                return new TcmbEvdsResponse();
            }
            if (response.getItems() == null) {
                response.setItems(List.of());
            }
            return response;
        } catch (RestClientException exception) {
            log.error("Failed to fetch TCMB EVDS data. chunkStart={}, chunkEnd={}, seriesCodesCount={}, series={}",
                    startDate, endDate, seriesCodes.size(), joinedSeriesCodes, exception);
            throw new DataProviderException("Failed to fetch TCMB EVDS data", exception);
        }
    }

    String buildUrl(String seriesCode, LocalDate startDate, LocalDate endDate) {
        String baseUrl = marketProperties.getProviders().getTcmb().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new DataProviderException("TCMB base URL is missing");
        }

        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = normalizedBaseUrl + "/series={series}&startDate={startDate}&endDate={endDate}&type=json&frequency=1";

        return UriComponentsBuilder.fromUriString(path)
                .buildAndExpand(
                        seriesCode,
                        startDate.format(DATE_FORMATTER),
                        endDate.format(DATE_FORMATTER)
                )
                .toUriString();
    }

    private String joinSeriesCodes(List<String> seriesCodes) {
        StringJoiner joiner = new StringJoiner("-");
        for (String seriesCode : seriesCodes) {
            if (seriesCode == null || seriesCode.isBlank()) {
                throw new DataProviderException("TCMB series code is required");
            }
            joiner.add(seriesCode);
        }
        return joiner.toString();
    }

    private void validateRequest(List<String> seriesCodes, LocalDate startDate, LocalDate endDate) {
        if (seriesCodes == null || seriesCodes.isEmpty()) {
            throw new DataProviderException("TCMB series codes are required");
        }
        if (startDate == null || endDate == null) {
            throw new DataProviderException("TCMB startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new DataProviderException("TCMB endDate cannot be before startDate");
        }
    }
}
