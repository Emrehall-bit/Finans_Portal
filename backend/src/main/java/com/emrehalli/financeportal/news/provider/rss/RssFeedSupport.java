package com.emrehalli.financeportal.news.provider.rss;

import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RssFeedSupport {

    private static final DateTimeFormatter RSS_DATE_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH);
    private static final List<DateTimeFormatter> FALLBACK_DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    );
    private static final Pattern XML_ENCODING_PATTERN =
            Pattern.compile("<\\?xml[^>]*encoding=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_SRC_PATTERN =
            Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public String decodeResponseBody(ResponseEntity<byte[]> response, Logger logger, String feedName) {
        byte[] body = response == null ? null : response.getBody();
        if (body == null || body.length == 0) {
            return "";
        }

        Charset contentTypeCharset = resolveCharsetFromContentType(response.getHeaders());
        if (contentTypeCharset != null) {
            return new String(body, contentTypeCharset);
        }

        Charset xmlCharset = resolveCharsetFromXmlDeclaration(body, logger, feedName);
        if (xmlCharset != null) {
            return new String(body, xmlCharset);
        }

        return new String(body, StandardCharsets.UTF_8);
    }

    public LocalDateTime parsePublishedAt(String rawValue, Logger logger, String feedName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return ZonedDateTime.parse(rawValue.trim(), RSS_DATE_FORMATTER).toLocalDateTime();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(rawValue.trim()).toLocalDateTime();
            } catch (Exception ignored) {
                for (DateTimeFormatter formatter : FALLBACK_DATE_FORMATTERS) {
                    try {
                        return LocalDateTime.parse(rawValue.trim(), formatter);
                    } catch (Exception ignoredFormatter) {
                        // try next format
                    }
                }
                logger.warn("Failed to parse {} feed date. dateRaw: {}", feedName, rawValue);
                return null;
            }
        }
    }

    public String resolveExternalId(String providerName, String guid, String link) {
        if (guid != null && !guid.isBlank()) {
            return guid.trim();
        }
        if (link == null || link.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(link.trim().getBytes(StandardCharsets.UTF_8));
            return providerName + "-" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return providerName + "-" + Math.abs(link.trim().hashCode());
        }
    }

    public String resolveXmlLink(Element entry) {
        Element linkElement = entry.selectFirst("link[href]");
        if (linkElement != null) {
            return clean(linkElement.absUrl("href"));
        }
        return clean(text(entry, "link"));
    }

    public String resolveImageUrl(Element entry, String htmlDescription) {
        if (entry == null) {
            return extractFirstImageUrlFromHtml(htmlDescription);
        }

        Element mediaContent = entry.selectFirst("media|content[url], media\\:content[url]");
        if (mediaContent != null) {
            String mediaUrl = clean(mediaContent.attr("url"));
            if (mediaUrl != null) {
                return mediaUrl;
            }
        }

        Element enclosure = entry.selectFirst("enclosure[url]");
        if (enclosure != null) {
            String enclosureUrl = clean(enclosure.attr("url"));
            if (enclosureUrl != null) {
                return enclosureUrl;
            }
        }

        String imageTagUrl = clean(text(entry, "image > url"));
        if (imageTagUrl != null) {
            return imageTagUrl;
        }

        return extractFirstImageUrlFromHtml(htmlDescription);
    }

    public String extractTextFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        String unescapedHtml = Parser.unescapeEntities(html, false);
        String parsedText = clean(Jsoup.parseBodyFragment(unescapedHtml).text());
        if (parsedText != null) {
            return parsedText;
        }

        String stripped = clean(
                unescapedHtml
                        .replaceAll("<[^>]+>", " ")
                        .replaceAll("\\s+", " ")
        );
        return stripped;
    }

    public String extractFirstImageUrlFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        Matcher rawMatcher = IMG_SRC_PATTERN.matcher(html);
        if (rawMatcher.find()) {
            return clean(rawMatcher.group(1));
        }

        String unescapedHtml = Parser.unescapeEntities(html, false);
        Matcher unescapedMatcher = IMG_SRC_PATTERN.matcher(unescapedHtml);
        if (unescapedMatcher.find()) {
            return clean(unescapedMatcher.group(1));
        }

        Document document = Jsoup.parseBodyFragment(unescapedHtml);
        Element image = document.selectFirst("img[src]");
        if (image == null) {
            return null;
        }

        return clean(image.attr("src"));
    }

    public String extractPreferredImageUrlFromDocument(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            return null;
        }

        Document document = Jsoup.parse(html, baseUrl);
        Element ogImage = document.selectFirst("meta[property=og:image][content], meta[name=og:image][content]");
        if (ogImage != null) {
            String content = clean(ogImage.attr("content"));
            if (content != null) {
                return content;
            }
        }

        Element twitterImage = document.selectFirst("meta[name=twitter:image][content], meta[property=twitter:image][content]");
        if (twitterImage != null) {
            String content = clean(twitterImage.attr("content"));
            if (content != null) {
                return content;
            }
        }

        Element image = document.selectFirst("img[src]");
        if (image == null) {
            return null;
        }

        return clean(image.absUrl("src").isBlank() ? image.attr("src") : image.absUrl("src"));
    }

    public String html(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element == null ? null : clean(element.html());
    }

    public String text(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element == null ? null : clean(element.text());
    }

    public String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    public String clean(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\u00A0', ' ').trim();
        return normalized.isEmpty() ? null : normalized;
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

    private Charset resolveCharsetFromXmlDeclaration(byte[] body, Logger logger, String feedName) {
        String asciiPrefix = new String(body, 0, Math.min(body.length, 256), StandardCharsets.US_ASCII);
        Matcher matcher = XML_ENCODING_PATTERN.matcher(asciiPrefix);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Charset.forName(matcher.group(1).trim());
        } catch (Exception e) {
            logger.warn("Unsupported {} feed xml charset. encoding: {}", feedName, matcher.group(1));
            return null;
        }
    }
}




