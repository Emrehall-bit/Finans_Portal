package com.emrehalli.financeportal.logconsumer.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class OpenSearchWriter {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchWriter.class);
    private static final DateTimeFormatter INDEX_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final String opensearchUrl;
    private final String indexPrefix;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenSearchWriter(
            @Value("${opensearch.url}") String opensearchUrl,
            @Value("${opensearch.index-prefix}") String indexPrefix) {
        this.opensearchUrl = opensearchUrl;
        this.indexPrefix = indexPrefix;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    public void bulkIndex(List<Map<String, Object>> docs) {
        if (docs.isEmpty()) return;

        StringBuilder body = new StringBuilder();
        int serialized = 0;
        for (Map<String, Object> doc : docs) {
            try {
                String index = resolveIndex(doc);
                body.append("{\"index\":{\"_index\":\"").append(index).append("\"}}\n");
                body.append(mapper.writeValueAsString(doc)).append("\n");
                serialized++;
            } catch (Exception e) {
                log.warn("Serialization error, skipping doc: {}", e.getMessage());
            }
        }

        if (serialized == 0) return;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(opensearchUrl + "/_bulk"))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String snippet = response.body().substring(0, Math.min(300, response.body().length()));
                log.error("OpenSearch bulk error [{}]: {}", response.statusCode(), snippet);
            } else if (response.body().contains("\"errors\":true")) {
                log.warn("Bulk partial errors for {} docs (check mapping)", serialized);
            } else {
                log.info("Indexed {} documents", serialized);
            }
        } catch (Exception e) {
            log.error("OpenSearch unreachable, dropping {} docs: {}", serialized, e.getMessage());
        }
    }

    private String resolveIndex(Map<String, Object> doc) {
        Object ts = doc.get("timestamp");
        if (ts instanceof String s && !s.isBlank()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                return indexPrefix + "-" + odt.withOffsetSameInstant(ZoneOffset.UTC).format(INDEX_DATE);
            } catch (Exception ignored) {}
        }
        return indexPrefix + "-" + LocalDate.now(ZoneOffset.UTC).format(INDEX_DATE);
    }
}
