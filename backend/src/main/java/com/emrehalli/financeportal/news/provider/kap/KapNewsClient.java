package com.emrehalli.financeportal.news.provider.kap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.provider.kap.dto.KapDisclosureBasic;
import com.emrehalli.financeportal.news.provider.kap.dto.KapDisclosureItem;
import com.emrehalli.financeportal.news.provider.kap.dto.KapDisclosureListRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class KapNewsClient {

    private static final Logger logger = LogManager.getLogger(KapNewsClient.class);

    private static final DateTimeFormatter KAP_REQUEST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter KAP_PUBLISH_DATE_FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter KAP_PUBLISH_DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter KAP_PUBLISH_DATE_DATE_ONLY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final List<String> DETAIL_SELECTORS = List.of(
            "div.disclosure-content",
            "div.content-text",
            "div.bildirim-icerik",
            "div.icerik",
            "div.notification-content",
            "article.content",
            "div#disclosureContent",
            "div.sgbf-bildirim",
            "main"
    );

    private final RestTemplate restTemplate;
    private final KapNewsProperties properties;
    private final ObjectMapper objectMapper;

    public KapNewsClient(RestTemplate restTemplate, KapNewsProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<NewsItemDto> fetchDisclosures(LocalDate fromDate, LocalDate toDate) {
        return fetchDisclosures(fromDate, toDate, null);
    }

    public List<NewsItemDto> fetchDisclosures(LocalDate fromDate, LocalDate toDate, String stockCodeFilter) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        String from = fromDate.format(KAP_REQUEST_DATE_FORMAT);
        String to = toDate.format(KAP_REQUEST_DATE_FORMAT);

        logger.info("KAP disclosure list request. from: {}, to: {}, disclosureTypes: {}, memberTypes: {}, stockCodeFilter: {}",
                from, to, properties.getDisclosureTypes(), properties.getMemberTypes(), stockCodeFilter);

        KapDisclosureListRequest request = new KapDisclosureListRequest(
                from, to,
                properties.getDisclosureTypes(),
                properties.getMemberTypes()
        );

        HttpHeaders headers = buildJsonHeaders();
        HttpEntity<KapDisclosureListRequest> entity = new HttpEntity<>(request, headers);
        String url = properties.normalizedBaseUrl() + "/tr/api/disclosure/list/main";

        // Fetch raw String first so we can log the sample on deserialize failure
        String rawBody;
        try {
            ResponseEntity<String> rawResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            rawBody = rawResponse.getBody();
        } catch (Exception ex) {
            logger.error("KAP disclosure list HTTP request failed. from: {}, to: {}, reason: {}", from, to, ex.getMessage(), ex);
            return List.of();
        }

        if (rawBody == null || rawBody.isBlank()) {
            logger.info("KAP disclosure list returned empty body. from: {}, to: {}", from, to);
            return List.of();
        }

        List<KapDisclosureItem> items;
        try {
            items = objectMapper.readValue(rawBody, new TypeReference<List<KapDisclosureItem>>() {});
        } catch (Exception ex) {
            String sample = rawBody.length() > 500 ? rawBody.substring(0, 500) + "..." : rawBody;
            logger.error("KAP disclosure list deserialize failed. from: {}, to: {}, reason: {}, responseSample: {}",
                    from, to, ex.getMessage(), sample);
            return List.of();
        }

        if (items == null || items.isEmpty()) {
            logger.info("KAP disclosure list returned no items. from: {}, to: {}", from, to);
            return List.of();
        }

        logger.info("KAP disclosure list returned {} items. from: {}, to: {}", items.size(), from, to);

        List<NewsItemDto> result = new ArrayList<>();
        int detailParseFailed = 0;

        for (KapDisclosureItem item : items) {
            KapDisclosureBasic basic = item.getDisclosureBasic();
            if (basic == null) {
                logger.debug("KAP item skipped: disclosureBasic is null.");
                continue;
            }

            if (stockCodeFilter != null && !stockCodeFilter.equalsIgnoreCase(basic.getStockCode())) {
                continue;
            }

            if (result.size() >= properties.getMaxResults()) break;

            String detailText = null;
            if (properties.isFetchDetailEnabled() && basic.getDisclosureIndex() != null) {
                try {
                    detailText = fetchDisclosureDetailHtml(basic.getDisclosureIndex());
                } catch (Exception ex) {
                    detailParseFailed++;
                    logger.debug("KAP detail parse failed. disclosureIndex: {}, reason: {}", basic.getDisclosureIndex(), ex.getMessage());
                }
            }

            NewsItemDto dto = toNewsItemDto(basic, detailText);
            if (dto == null) {
                logger.debug("KAP item skipped: could not build NewsItemDto. disclosureId: {}", basic.getDisclosureId());
                continue;
            }

            result.add(dto);
        }

        logger.info("KAP disclosure fetch completed. from: {}, to: {}, returned: {}, mapped: {}, detailParseFailed: {}",
                from, to, items.size(), result.size(), detailParseFailed);

        return List.copyOf(result);
    }

    public String fetchDisclosureDetailHtml(Long disclosureIndex) {
        if (disclosureIndex == null) return null;

        String url = properties.normalizedBaseUrl() + "/tr/Bildirim/" + disclosureIndex;
        HttpHeaders headers = buildBrowserHeaders();

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
        );

        String html = response.getBody();
        if (html == null || html.isBlank()) return null;

        Document doc = Jsoup.parse(html, url);

        for (String selector : DETAIL_SELECTORS) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String text = el.text().trim();
                if (!text.isBlank() && text.length() > 50) {
                    return text;
                }
            }
        }

        return null;
    }

    private NewsItemDto toNewsItemDto(KapDisclosureBasic basic, String detailText) {
        String externalId = resolveExternalId(basic);
        if (externalId == null) {
            return null;
        }

        String url = resolveUrl(basic);
        String title = resolveTitle(basic);
        if (title == null) {
            return null;
        }

        String summary = resolveSummary(basic, detailText);
        LocalDateTime publishedAt = parsePublishDate(basic.getPublishDate());
        String category = mapCategory(basic.getDisclosureClass(), basic.getDisclosureType());
        String qualityStatus = (detailText != null && detailText.length() >= 500)
                ? NewsQualityStatus.FULL_CONTENT.name()
                : NewsQualityStatus.KAP_DISCLOSURE.name();

        return NewsItemDto.builder()
                .externalId(externalId)
                .title(title)
                .summary(summary)
                .source("KAP")
                .provider(NewsProviderType.KAP.name())
                .language(properties.getDefaultLanguage())
                .regionScope(properties.getDefaultRegionScope())
                .category(category)
                .relatedSymbol(normalizeSymbol(basic.getStockCode()))
                .url(url)
                .publishedAt(publishedAt)
                .qualityStatus(qualityStatus)
                .isKapDisclosure(true)
                .disclosureType(resolveDisclosureType(basic))
                .build();
    }

    private String resolveExternalId(KapDisclosureBasic basic) {
        if (hasText(basic.getDisclosureId())) {
            return "KAP-" + basic.getDisclosureId().trim();
        }
        if (basic.getDisclosureIndex() != null) {
            return "KAP-IDX-" + basic.getDisclosureIndex();
        }
        return null;
    }

    private String resolveUrl(KapDisclosureBasic basic) {
        if (basic.getDisclosureIndex() == null) return null;
        return properties.normalizedBaseUrl() + "/tr/Bildirim/" + basic.getDisclosureIndex();
    }

    private String resolveTitle(KapDisclosureBasic basic) {
        String stockCode = basic.getStockCode();
        String title = basic.getTitle();
        if (hasText(stockCode) && hasText(title)) {
            return stockCode.trim().toUpperCase(Locale.ROOT) + " - " + title.trim();
        }
        if (hasText(title)) return title.trim();
        if (hasText(basic.getCompanyTitle())) return basic.getCompanyTitle().trim();
        return null;
    }

    private String resolveSummary(KapDisclosureBasic basic, String detailText) {
        if (hasText(detailText)) return detailText.trim();
        if (hasText(basic.getSummary())) return basic.getSummary().trim();
        return null;
    }

    private String resolveDisclosureType(KapDisclosureBasic basic) {
        String disclosureClass = basic.getDisclosureClass();
        if (!hasText(disclosureClass)) return "GENERAL";
        return switch (disclosureClass.trim().toUpperCase(Locale.ROOT)) {
            case "FR" -> "FINANCIAL";
            case "ODA" -> "SPECIAL";
            case "GKB" -> "SPECIAL";
            default -> "GENERAL";
        };
    }

    private String mapCategory(String disclosureClass, String disclosureType) {
        if (!hasText(disclosureClass)) return properties.getDefaultCategory();
        return switch (disclosureClass.trim().toUpperCase(Locale.ROOT)) {
            case "FR" -> "FINANCIAL_REPORT";
            case "ODA" -> "SPECIAL_DISCLOSURE";
            case "GKB" -> "GENERAL_MEETING";
            default -> properties.getDefaultCategory();
        };
    }

    private LocalDateTime parsePublishDate(String publishDate) {
        if (!hasText(publishDate)) return null;
        String trimmed = publishDate.trim();
        for (DateTimeFormatter formatter : List.of(KAP_PUBLISH_DATE_FULL, KAP_PUBLISH_DATE_SHORT, KAP_PUBLISH_DATE_DATE_ONLY)) {
            try {
                if (formatter == KAP_PUBLISH_DATE_DATE_ONLY) {
                    return LocalDate.parse(trimmed, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(trimmed, formatter);
            } catch (Exception ignored) {
            }
        }
        logger.warn("KAP publishDate could not be parsed. value: {}", publishDate);
        return null;
    }

    private String normalizeSymbol(String stockCode) {
        if (!hasText(stockCode)) return null;
        return stockCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Referer", properties.normalizedBaseUrl() + "/tr");
        headers.set("Origin", properties.normalizedBaseUrl());
        return headers;
    }

    private HttpHeaders buildBrowserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Referer", properties.normalizedBaseUrl() + "/tr");
        return headers;
    }
}
