package com.emrehalli.financeportal.market.provider.tcmb.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class EvdsClient {

    private static final Logger logger = LogManager.getLogger(EvdsClient.class);

    private final RestTemplate restTemplate;

    @Value("${evds.api.key}")
    private String apiKey;

    @Value("${evds.api.url}")
    private String baseUrl;

    public EvdsClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String fetchCurrencyData() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);

        String formattedStartDate = startDate.format(formatter);
        String formattedEndDate = endDate.format(formatter);

        String url = baseUrl
                + "/series=TP.DK.USD.A-TP.DK.EUR.A"
                + "&startDate=" + formattedStartDate
                + "&endDate=" + formattedEndDate
                + "&type=json";

        HttpHeaders headers = new HttpHeaders();
        headers.set("key", apiKey);
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
}