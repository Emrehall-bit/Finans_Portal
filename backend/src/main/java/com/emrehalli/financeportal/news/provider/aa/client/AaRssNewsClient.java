package com.emrehalli.financeportal.news.provider.aa.client;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AaRssNewsClient {

    private static final Logger logger = LogManager.getLogger(AaRssNewsClient.class);
    private static final DateTimeFormatter RSS_DATE_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH);
    private static final Pattern XML_ENCODING_PATTERN =
            Pattern.compile("<\\?xml[^>]*encoding=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final AaNewsProperties properties;

    public AaRssNewsClient(RestTemplate restTemplate, AaNewsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<NewsItemDto> fetchEconomyNews() {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    properties.getRssUrl(),
                    HttpMethod.GET,
                    null,
                    byte[].class
            );
            String payload = decodeResponseBody(response);
            if (payload == null || payload.isBlank()) {
                logger.warn("AA RSS response was empty");
                return List.of();
            }

            List<NewsItemDto> items = parse(payload);
            logger.info("AA feed parsed. url: {}, fetched: {}", properties.getRssUrl(), items.size());
            return items;
        } catch (RestClientException e) {
            logger.error("AA RSS fetch failed. url: {}", properties.getRssUrl(), e);
            return List.of();
        } catch (Exception e) {
            logger.error("Unexpected AA RSS fetch failure. url: {}", properties.getRssUrl(), e);
            return List.of();
        }
    }

    String decodeResponseBody(ResponseEntity<byte[]> response) {
        byte[] body = response == null ? null : response.getBody();
        if (body == null || body.length == 0) {
            return "";
        }

        Charset contentTypeCharset = resolveCharsetFromContentType(response.getHeaders());
        if (contentTypeCharset != null) {
            return new String(body, contentTypeCharset);
        }

        Charset xmlCharset = resolveCharsetFromXmlDeclaration(body);
        if (xmlCharset != null) {
            return new String(body, xmlCharset);
        }

        return new String(body, StandardCharsets.UTF_8);
    }

    List<NewsItemDto> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }

        try {
            List<NewsItemDto> xmlItems = parseXmlFeed(payload);
            if (!xmlItems.isEmpty()) {
                return xmlItems;
            }

            List<NewsItemDto> htmlItems = parseHtmlFeed(payload);
            if (!htmlItems.isEmpty()) {
                logger.info("AA feed fallback parser used. url: {}, fetched: {}", properties.getRssUrl(), htmlItems.size());
                return htmlItems;
            }

            logger.warn("AA feed did not contain parsable items. url: {}", properties.getRssUrl());
            return List.of();
        } catch (Exception e) {
            logger.error("Failed to parse AA feed. url: {}", properties.getRssUrl(), e);
            return List.of();
        }
    }

    private Charset resolveCharsetFromContentType(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }

        MediaType contentType = headers.getContentType();
        if (contentType == null || contentType.getCharset() == null) {
            return null;
        }

        return contentType.getCharset();
    }

    private Charset resolveCharsetFromXmlDeclaration(byte[] body) {
        String asciiPrefix = new String(body, 0, Math.min(body.length, 256), StandardCharsets.US_ASCII);
        Matcher matcher = XML_ENCODING_PATTERN.matcher(asciiPrefix);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Charset.forName(matcher.group(1).trim());
        } catch (Exception e) {
            logger.warn("Unsupported AA feed xml charset. encoding: {}", matcher.group(1));
            return null;
        }
    }

    private List<NewsItemDto> parseXmlFeed(String xml) {
        Document document = Jsoup.parse(xml, properties.getRssUrl(), Parser.xmlParser());
        Elements entries = document.select("channel > item, feed > entry");
        List<NewsItemDto> items = new ArrayList<>();

        for (Element entry : entries) {
            String title = text(entry, "title");
            String link = resolveXmlLink(entry);
            String guid = firstNonBlank(text(entry, "guid"), text(entry, "id"));
            String summary = firstNonBlank(
                    text(entry, "description"),
                    text(entry, "summary"),
                    text(entry, "content")
            );
            String pubDate = firstNonBlank(
                    text(entry, "pubDate"),
                    text(entry, "updated"),
                    text(entry, "published")
            );

            NewsItemDto item = buildItem(title, link, guid, summary, pubDate);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    private List<NewsItemDto> parseHtmlFeed(String html) {
        Document document = Jsoup.parse(html, properties.getRssUrl());
        Elements anchors = document.select("a[href]");
        Set<String> seenLinks = new LinkedHashSet<>();
        List<NewsItemDto> items = new ArrayList<>();

        for (Element anchor : anchors) {
            String link = clean(anchor.absUrl("href"));
            if (!isEconomyArticleLink(link) || !seenLinks.add(link)) {
                continue;
            }

            String title = clean(anchor.text());
            if (title == null || title.length() < 12) {
                Element article = anchor.closest("article");
                title = firstNonBlank(
                        title,
                        article == null ? null : text(article, "h1, h2, h3, h4")
                );
            }

            NewsItemDto item = buildItem(title, link, null, null, null);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    private NewsItemDto buildItem(String title, String link, String guid, String summary, String pubDate) {
        if (title == null || link == null) {
            return null;
        }

        return NewsItemDto.builder()
                .externalId(resolveExternalId(guid, link))
                .title(title)
                .summary(clean(summary))
                .source("Anadolu Ajansı")
                .provider(NewsProviderType.AA_RSS.name())
                .language(properties.getDefaultLanguage())
                .regionScope(properties.getDefaultRegionScope())
                .category(properties.getDefaultCategory())
                .relatedSymbol(null)
                .url(link)
                .publishedAt(parsePublishedAt(pubDate))
                .build();
    }

    private LocalDateTime parsePublishedAt(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return ZonedDateTime.parse(rawValue.trim(), RSS_DATE_FORMATTER).toLocalDateTime();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(rawValue.trim()).toLocalDateTime();
            } catch (Exception ignored) {
                logger.warn("Failed to parse AA feed date. dateRaw: {}", rawValue);
                return null;
            }
        }
    }

    private String resolveExternalId(String guid, String link) {
        if (guid != null && !guid.isBlank()) {
            return guid.trim();
        }
        if (link == null || link.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(link.trim().getBytes(StandardCharsets.UTF_8));
            return "AA_RSS-" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "AA_RSS-" + Math.abs(link.trim().hashCode());
        }
    }

    private String resolveXmlLink(Element entry) {
        Element linkElement = entry.selectFirst("link[href]");
        if (linkElement != null) {
            return clean(linkElement.absUrl("href"));
        }
        return text(entry, "link");
    }

    private boolean isEconomyArticleLink(String link) {
        if (link == null) {
            return false;
        }
        return link.contains("/tr/ekonomi/") || link.contains("/ekonomi/");
    }

    private String text(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element == null ? null : clean(element.text());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\u00A0', ' ').trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
