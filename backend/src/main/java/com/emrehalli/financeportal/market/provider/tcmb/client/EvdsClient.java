package com.emrehalli.financeportal.market.provider.tcmb.client;

import com.emrehalli.financeportal.config.EvdsProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class EvdsClient {

    private static final Logger logger = LogManager.getLogger(EvdsClient.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestTemplate restTemplate;
    private final EvdsProperties evdsProperties;

    public EvdsClient(RestTemplate restTemplate, EvdsProperties evdsProperties) {
        this.restTemplate = restTemplate;
        this.evdsProperties = evdsProperties;
    }

    public String fetchCurrencyData() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);

        String formattedStartDate = startDate.format(DATE_FORMATTER);
        String formattedEndDate = endDate.format(DATE_FORMATTER);

        List<String> apiCodes = evdsProperties.getSeries().stream()
                .filter(seriesItem -> seriesItem.getApiCode() != null && !seriesItem.getApiCode().isBlank())
                .map(EvdsProperties.SeriesItem::getApiCode)
                .toList();

        if (apiCodes.isEmpty()) {
            logger.warn("No EVDS series apiCode is configured. Returning empty payload.");
            return "";
        }

        String url = evdsProperties.getApi().getUrl()
                + "/series=" + String.join("-", apiCodes)
                + "&startDate=" + formattedStartDate
                + "&endDate=" + formattedEndDate
                + "&type=json";

        HttpHeaders headers = new HttpHeaders();
        headers.set("key", evdsProperties.getApi().getKey());
        headers.setAccept(MediaType.parseMediaTypes("application/json"));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        logger.info("Sending EVDS request");
        logger.debug("EVDS request URL: {}", url);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
        );

        logger.info("EVDS response received with status: {}", response.getStatusCode());
        logger.debug("EVDS raw response body: {}", response.getBody());

        return response.getBody();
    }

    public boolean isEnabled() {
        return evdsProperties.isEnabled();
    }
}
