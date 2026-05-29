package com.emrehalli.financeportal.market.provider.macro.tcmb;

import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroObservationParseResult;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroSeriesField;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroSeriesRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TcmbMacroProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String evdsPostUrl;

    public TcmbMacroProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${market.providers.tcmb-evds-post.url:https://evds3.tcmb.gov.tr/igmevdsms-dis/fe}") String evdsPostUrl) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.evdsPostUrl = evdsPostUrl;
    }

    public List<MacroObservationParseResult> fetchSeries(MacroSeriesRequest request) {
        Map<String, Object> requestBody = buildRequest(request);
        try {
            String responseBody = restClient.post()
                    .uri(evdsPostUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("TCMB EVDS POST returned empty body for series={}", request.evdsSeries());
                return List.of();
            }
            return parseItems(responseBody, request.fields());
        } catch (RestClientException ex) {
            log.warn("TCMB EVDS POST request failed for series={}: {}", request.evdsSeries(), ex.getMessage());
            throw new DataProviderException("TCMB EVDS fetch failed for series=" + request.evdsSeries(), ex);
        }
    }

    private Map<String, Object> buildRequest(MacroSeriesRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "json");
        body.put("series", request.evdsSeries());
        body.put("aggregationTypes", request.aggregationType());
        body.put("startDate", request.startDate());
        body.put("endDate", request.endDate());
        body.put("formulas", request.formula());
        body.put("frequency", "5");
        body.put("decimal", "2");
        body.put("decimalSeparator", ".");
        body.put("dateFormat", request.dateFormat());
        body.put("lang", "tr");
        body.put("sira", "0");
        body.put("yon", "0");
        body.put("groupSeperator", true);
        body.put("groupSeparator", true);
        body.put("isRaporSayfasi", false);
        body.put("ozelFormuller", List.of());
        return body;
    }

    List<MacroObservationParseResult> parseItems(String responseBody, List<MacroSeriesField> fields) {
        List<MacroObservationParseResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.get("items");
            if (items == null || !items.isArray()) {
                log.warn("TCMB EVDS response has no 'items' array");
                return List.of();
            }
            for (JsonNode item : items) {
                parseItem(item, fields, results);
            }
        } catch (Exception ex) {
            log.warn("Failed to parse TCMB EVDS response: {}", ex.getMessage());
            log.debug("TCMB EVDS parse error", ex);
        }
        return results;
    }

    private void parseItem(JsonNode item, List<MacroSeriesField> fields,
                           List<MacroObservationParseResult> results) {
        String tarih = textOrNull(item, "Tarih");
        if (tarih == null) {
            log.debug("Skipping item with missing Tarih field");
            return;
        }

        LocalDate observationDate;
        try {
            observationDate = YearMonth.parse(tarih).atDay(1);
        } catch (DateTimeParseException ex) {
            log.debug("Skipping item with unparseable date: {}", tarih);
            return;
        }

        for (MacroSeriesField field : fields) {
            BigDecimal value = decimalOrNull(item, field.fieldName());
            if (value != null) {
                results.add(new MacroObservationParseResult(
                        field.indicatorCode(), tarih, observationDate, value, field.valueType()));
            }
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String text = n.asText();
        return (text == null || text.isBlank()) ? null : text;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String text = n.asText();
        if (text == null || text.isBlank()) return null;
        try {
            // "." decimal + "," thousands (e.g. "-1,023.00") â†’ strip commas
            // "," decimal + no "." (e.g. "4,18") â†’ replace comma with dot
            String normalized = text.contains(".") ? text.replace(",", "") : text.replace(",", ".");
            return new BigDecimal(normalized.trim());
        } catch (NumberFormatException ex) {
            log.debug("Cannot parse decimal '{}' for field '{}'", text, field);
            return null;
        }
    }
}




