package com.emrehalli.financeportal.news.provider.kap;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KapNewsClient {

    private static final Logger logger = LogManager.getLogger(KapNewsClient.class);
    private static final Pattern PUBLISHED_AT_PATTERN = Pattern.compile("(\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2})");
    private static final DateTimeFormatter PUBLISHED_AT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\b([A-Z0-9]{3,6})\\b");

    private final RestTemplate restTemplate;
    private final KapNewsProperties properties;
    private final RssFeedSupport rssFeedSupport;

    public KapNewsClient(RestTemplate restTemplate,
                         KapNewsProperties properties,
                         RssFeedSupport rssFeedSupport) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.rssFeedSupport = rssFeedSupport;
    }

    public List<NewsItemDto> fetchLatestNews() {
        if (!properties.isEnabled()) {
            return List.of();
        }

        String baseUrl = normalizeBaseUrl();
        String html = restTemplate.getForObject(baseUrl + "/tr", String.class);
        return parseNews(html, baseUrl, null);
    }

    public List<NewsItemDto> fetchCompanyNews(String symbol) {
        if (!properties.isEnabled() || symbol == null || symbol.isBlank()) {
            return List.of();
        }

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        String query = URLEncoder.encode(normalizedSymbol, StandardCharsets.UTF_8);
        String baseUrl = normalizeBaseUrl();
        String html = restTemplate.getForObject(baseUrl + "/tr/search/" + query + "/1", String.class);
        return parseNews(html, baseUrl, normalizedSymbol);
    }

    List<NewsItemDto> parseNews(String html, String baseUrl, String relatedSymbol) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(html, baseUrl);
        Map<String, NewsItemDto> itemsByUrl = new LinkedHashMap<>();

        for (Element anchor : document.select("a[href]")) {
            String title = rssFeedSupport.clean(anchor.text());
            String url = rssFeedSupport.clean(anchor.absUrl("href"));
            if (title == null || url == null || !looksLikeNotification(url, title)) {
                continue;
            }

            Element context = resolveContext(anchor);
            String contextText = context == null ? title : rssFeedSupport.clean(context.text());
            LocalDateTime publishedAt = parsePublishedAt(contextText);
            String summary = summarize(contextText, title, publishedAt);
            String externalId = rssFeedSupport.resolveExternalId(NewsProviderType.KAP.name(), null, url);
            String resolvedSymbol = relatedSymbol != null ? relatedSymbol : extractRelatedSymbol(title, summary);
            String disclosureType = detectDisclosureType(title, summary);

            itemsByUrl.putIfAbsent(url, NewsItemDto.builder()
                    .externalId(externalId)
                    .title(title)
                    .summary(summary)
                    .source("KAP")
                    .provider(NewsProviderType.KAP.name())
                    .language(properties.getDefaultLanguage())
                    .regionScope(properties.getDefaultRegionScope())
                    .category(properties.getDefaultCategory())
                    .relatedSymbol(resolvedSymbol)
                    .url(url)
                    .publishedAt(publishedAt)
                    .qualityStatus(NewsQualityStatus.KAP_DISCLOSURE.name())
                    .isKapDisclosure(true)
                    .disclosureType(disclosureType)
                    .build());

            if (itemsByUrl.size() >= Math.max(properties.getMaxItems(), 1)) {
                break;
            }
        }

        List<NewsItemDto> items = new ArrayList<>(itemsByUrl.values());
        logger.info("KAP news fetch parsed: relatedSymbol={}, itemCount={}", relatedSymbol, items.size());
        return List.copyOf(items);
    }

    private String normalizeBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://www.kap.org.tr";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private boolean looksLikeNotification(String url, String title) {
        if (url == null || title == null) {
            return false;
        }

        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        return normalizedUrl.contains("/tr/")
                && !normalizedUrl.contains("/search/")
                && !normalizedUrl.contains("/about/")
                && !normalizedUrl.contains("/bist-sirketleri")
                && title.length() > 8;
    }

    private Element resolveContext(Element anchor) {
        Element current = anchor;
        for (int i = 0; i < 6 && current != null; i++) {
            String text = rssFeedSupport.clean(current.text());
            if (text != null && PUBLISHED_AT_PATTERN.matcher(text).find()) {
                return current;
            }
            current = current.parent();
        }
        return anchor.parent();
    }

    private LocalDateTime parsePublishedAt(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        Matcher matcher = PUBLISHED_AT_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            return null;
        }

        try {
            return LocalDateTime.parse(matcher.group(1), PUBLISHED_AT_FORMATTER);
        } catch (Exception ex) {
            logger.warn("Failed to parse KAP publishedAt: value={}", matcher.group(1));
            return null;
        }
    }

    private String summarize(String rawText, String title, LocalDateTime publishedAt) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        String summary = rawText.replace(title, "")
                .replace("Bildirim", "")
                .replace("Gönderim Tarihi", "")
                .trim();

        if (publishedAt != null) {
            summary = summary.replace(publishedAt.format(PUBLISHED_AT_FORMATTER), "").trim();
        } else {
            summary = PUBLISHED_AT_PATTERN.matcher(summary).replaceAll("").trim();
        }

        summary = summary.replaceAll("\\s+", " ").trim();
        return summary.isBlank() ? null : summary;
    }

    private String detectDisclosureType(String title, String summary) {
        String content = ((title == null ? "" : title) + " " + (summary == null ? "" : summary)).toLowerCase(Locale.ROOT);
        if (content.contains("finansal") || content.contains("bilan") || content.contains("faaliyet raporu")) {
            return "FINANCIAL";
        }
        if (content.contains("hak kullan") || content.contains("temett") || content.contains("bedelsiz") || content.contains("sermaye art")) {
            return "RIGHTS";
        }
        if (content.contains("ozel durum") || content.contains("özel durum") || content.contains("genel kurul")
                || content.contains("yönetim kurulu") || content.contains("duyuru")) {
            return "SPECIAL";
        }
        return "GENERAL";
    }

    private String extractRelatedSymbol(String title, String summary) {
        String combined = (title == null ? "" : title) + " " + (summary == null ? "" : summary);
        Matcher matcher = SYMBOL_PATTERN.matcher(combined);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.length() >= 3 && candidate.length() <= 6 && !candidate.equals("KAP")) {
                return candidate;
            }
        }
        return null;
    }
}
