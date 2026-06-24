package com.emrehalli.financeportal.market.provider.fx;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Akbank FX market data provider.
 */
@Component
@Slf4j
public class AkbankFxProvider extends AbstractFxProvider implements MarketDataProvider {

    private static final String ENDPOINT = "https://www.akbank.com/_layouts/15/Akbank/CalcTools/Ajax.aspx/GetDovizKurlari";
    private static final DateTimeFormatter UPDATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AkbankFxProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate, objectMapper);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceName() {
        return SourceName.AKBANK.name();
    }

    @CircuitBreaker(name = "akbankFx", fallbackMethod = "fetchFallback")
    @Override
    public List<FxRateDto> fetch() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    ENDPOINT,
                    new HttpEntity<>(Map.of("kurTuru", "8"), headers),
                    String.class
            );

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("AKBANK FX provider returned empty payload. Provider skipped.");
                return List.of();
            }

            List<FxRateDto> rates = mapRates(objectMapper.readTree(body));
            if (rates.isEmpty()) {
                log.warn("AKBANK FX provider returned no parsable rates. Provider skipped.");
            }
            return rates;
        } catch (HttpStatusCodeException exception) {
            log.warn("AKBANK FX provider request failed. statusCode={}, errorMessage={}",
                    exception.getStatusCode().value(),
                    "HTTP " + exception.getStatusCode().value() + " " + exception.getStatusText());
            log.debug("AKBANK FX provider request failed with stacktrace.", exception);
            throw new DataProviderException("AKBANK FX provider request failed", exception);
        } catch (Exception exception) {
            log.warn("AKBANK FX provider request failed. errorMessage={}", exception.getMessage());
            log.debug("AKBANK FX provider request failed with stacktrace.", exception);
            throw new DataProviderException("AKBANK FX provider request failed", exception);
        }
    }

    private List<FxRateDto> fetchFallback(Throwable throwable) {
        log.warn("Akbank FX circuit breaker fallback triggered, returning empty rate list. reason={}", throwable.toString());
        return List.of();
    }

    private List<FxRateDto> mapRates(JsonNode root) {
        JsonNode rows = root.path("d").path("Data").path("DovizKurlari");
        if (!rows.isArray()) {
            return List.of();
        }

        List<FxRateDto> rates = new ArrayList<>();
        for (JsonNode row : rows) {
            String currencyCode = text(row, "AlfaKod");
            BigDecimal buyPrice = decimal(row, "DovizAlis");
            BigDecimal sellPrice = decimal(row, "DovizSatis");
            if (currencyCode == null || (buyPrice == null && sellPrice == null)) {
                continue;
            }

            rates.add(FxRateDto.builder()
                    .sourceName(SourceName.AKBANK)
                    .currencyCode(normalizeCurrencyCode(currencyCode))
                    .buyPrice(buyPrice)
                    .sellPrice(sellPrice)
                    .dataTimestamp(timestamp(row, "KurGuncellemeZamani"))
                    .build());
        }
        return List.copyOf(rates);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        String text = text(node, fieldName);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDateTime timestamp(JsonNode node, String fieldName) {
        String text = text(node, fieldName);
        if (text == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(text, UPDATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return LocalDateTime.now();
        }
    }

    private String normalizeCurrencyCode(String rawValue) {
        return rawValue.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
