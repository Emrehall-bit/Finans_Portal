package com.emrehalli.financeportal.market.provider.kap.client;

import com.emrehalli.financeportal.config.KapProperties;
import com.emrehalli.financeportal.market.provider.kap.dto.KapDisclosureResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class KapClient {

    private static final Logger logger = LogManager.getLogger(KapClient.class);

    private final KapProperties kapProperties;

    public KapClient(KapProperties kapProperties) {
        this.kapProperties = kapProperties;
    }

    public KapDisclosureResponse fetchRecentDisclosures() {
        if (!kapProperties.isEnabled()) {
            logger.debug("KAP event fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (kapProperties.getBaseUrl() == null || kapProperties.getBaseUrl().isBlank()) {
            logger.warn("KAP provider is enabled but base URL is missing. Event fetch skipped.");
            return emptyResponse();
        }

        List<String> searchTerms = kapProperties.getSearchTerms().stream()
                .filter(term -> term != null && !term.isBlank())
                .map(String::trim)
                .toList();

        if (searchTerms.isEmpty()) {
            logger.warn("KAP provider is enabled but no search terms are configured. Event fetch skipped.");
            return emptyResponse();
        }

        logger.info("KAP event fetch started for {} search terms against {}", searchTerms.size(), kapProperties.getBaseUrl());

        LocalDateTime fetchedAt = LocalDateTime.now();
        Map<String, Map<String, Object>> uniqueItems = new LinkedHashMap<>();

        for (String term : searchTerms) {
            String searchUrl = kapProperties.getBaseUrl() + "/tr/search/"
                    + URLEncoder.encode(term, StandardCharsets.UTF_8).replace("+", "%20") + "/1";

            try {
                Document searchDocument = Jsoup.connect(searchUrl)
                        .userAgent("Mozilla/5.0")
                        .timeout(resolveTimeout())
                        .get();

                Set<String> disclosureUrls = searchDocument.select("a[href*=/tr/Bildirim/]").stream()
                        .map(element -> element.absUrl("href"))
                        .filter(url -> url != null && !url.isBlank())
                        .limit(Math.max(1, kapProperties.getMaxItems()))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

                for (String disclosureUrl : disclosureUrls) {
                    uniqueItems.putIfAbsent(disclosureUrl, fetchDisclosureDetail(disclosureUrl, fetchedAt));
                }
            } catch (IOException e) {
                logger.warn("KAP search fetch failed for term {} via {}", term, searchUrl, e);
            }
        }

        List<Map<String, Object>> items = uniqueItems.values().stream()
                .filter(map -> map != null && !map.isEmpty())
                .limit(Math.max(1, kapProperties.getMaxItems()))
                .toList();

        logger.info("KAP event fetch completed with {} disclosure items", items.size());
        return KapDisclosureResponse.builder()
                .fetchedAt(fetchedAt)
                .items(items)
                .build();
    }

    private Map<String, Object> fetchDisclosureDetail(String disclosureUrl, LocalDateTime fetchedAt) {
        try {
            Document disclosureDocument = Jsoup.connect(disclosureUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(resolveTimeout())
                    .get();

            String bodyText = disclosureDocument.text();
            String title = disclosureDocument.select("h1, h2").stream()
                    .map(Element::text)
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .findFirst()
                    .orElseGet(() -> extractValue(bodyText, "Özet Bilgi", "Yapılan Açıklama Güncelleme mi"));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", disclosureUrl);
            item.put("title", title == null ? disclosureUrl : title);
            item.put("publishedAt", extractValue(bodyText, "Gönderim Tarihi", "Bildirim Tipi"));
            item.put("disclosureType", extractValue(bodyText, "Bildirim Tipi", "Yıl"));
            item.put("summary", extractSummary(bodyText));
            item.put("symbol", extractSymbol(disclosureDocument, bodyText));
            item.put("rawPayload", truncate(bodyText, 4000));
            item.put("fetchedAt", fetchedAt);
            return item;
        } catch (IOException e) {
            logger.warn("KAP disclosure detail fetch failed via {}", disclosureUrl, e);
            return new LinkedHashMap<>();
        }
    }

    private String extractSummary(String bodyText) {
        String summary = extractValue(bodyText, "Özet Bilgi", "Yapılan Açıklama Güncelleme mi");
        return summary != null && !summary.isBlank()
                ? summary
                : extractValue(bodyText, "Açıklamalar", "Bildirim Ekleri");
    }

    private String extractSymbol(Document document, String bodyText) {
        String headerText = document.select("h1, h2").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");

        java.util.regex.Matcher headerMatcher = java.util.regex.Pattern.compile("([A-Z]{2,6})$").matcher(headerText);
        if (headerMatcher.find()) {
            return headerMatcher.group(1);
        }

        java.util.regex.Matcher bodyMatcher = java.util.regex.Pattern.compile("\\b([A-Z]{2,6})\\b").matcher(bodyText);
        return bodyMatcher.find() ? bodyMatcher.group(1) : null;
    }

    private String extractValue(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return null;
        }

        int valueStart = startIndex + start.length();
        int endIndex = end == null ? -1 : text.indexOf(end, valueStart);
        String extracted = endIndex > valueStart
                ? text.substring(valueStart, endIndex)
                : text.substring(valueStart);

        extracted = extracted.replaceAll("\\s+", " ").trim();
        return extracted.isBlank() ? null : extracted;
    }

    private int resolveTimeout() {
        return kapProperties.getHttp().getReadTimeoutMs() != null
                ? kapProperties.getHttp().getReadTimeoutMs()
                : 5000;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    private KapDisclosureResponse emptyResponse() {
        return KapDisclosureResponse.builder()
                .fetchedAt(LocalDateTime.now())
                .items(List.of())
                .build();
    }
}
