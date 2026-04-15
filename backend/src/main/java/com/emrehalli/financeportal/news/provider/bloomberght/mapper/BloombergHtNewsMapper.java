package com.emrehalli.financeportal.news.provider.bloomberght.mapper;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsRegionScope;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class BloombergHtNewsMapper {

    private static final String BASE_URL = "https://www.bloomberght.com";

    public List<NewsItemDto> map(Document document) {
        if (document == null) {
            return List.of();
        }

        List<Element> candidates = collectCandidateAnchors(document);
        List<NewsItemDto> mapped = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        for (Element candidate : candidates) {
            String url = normalizeUrl(candidate.absUrl("href"));
            if (!isValidNewsUrl(url) || !seenUrls.add(url)) {
                continue;
            }

            String title = extractTitle(candidate);
            if (isBlank(title) || title.length() < 15) {
                continue;
            }

            String summary = extractSummary(candidate);
            String category = extractCategory(candidate);
            LocalDateTime publishedAt = extractPublishedAt(candidate);

            mapped.add(
                    NewsItemDto.builder()
                            .externalId(buildExternalId(url))
                            .title(cleanText(title))
                            .summary(cleanText(summary))
                            .source("Bloomberg HT")
                            .provider(NewsProviderType.BLOOMBERG_HT.name())
                            .language("tr")
                            .regionScope(NewsRegionScope.LOCAL.name())
                            .category(cleanText(category))
                            .relatedSymbol(null)
                            .url(url)
                            .publishedAt(publishedAt != null ? publishedAt : LocalDateTime.now())
                            .build()
            );
        }

        return mapped;
    }

    /**
     * Sadece haber olma ihtimali yüksek anchor'ları toplar.
     * Tüm siteyi süpürmek yerine içerik alanlarına yakın bölgeleri hedefliyoruz.
     */
    private List<Element> collectCandidateAnchors(Document document) {
        Set<Element> result = new LinkedHashSet<>();

        String[] scopedSelectors = {
                "main a[href]",
                "article a[href]",
                "section a[href]",
                ".content a[href]",
                ".news a[href]",
                ".news-list a[href]",
                ".haber a[href]",
                ".haberler a[href]",
                ".markets a[href]",
                ".piyasalar a[href]"
        };

        for (String selector : scopedSelectors) {
            Elements elements = document.select(selector);
            for (Element element : elements) {
                if (looksLikeNewsAnchor(element)) {
                    result.add(element);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private boolean looksLikeNewsAnchor(Element anchor) {
        if (anchor == null) {
            return false;
        }

        String url = normalizeUrl(anchor.absUrl("href"));
        if (!isValidNewsUrl(url)) {
            return false;
        }

        String text = cleanText(anchor.text());
        if (isBlank(text) || text.length() < 15) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        // Menü / genel navigasyon / kısa alakasız metinleri ele
        return !lower.equals("canlı yayın")
                && !lower.equals("video")
                && !lower.equals("podcast")
                && !lower.equals("borsa")
                && !lower.equals("döviz")
                && !lower.equals("emtia")
                && !lower.equals("kripto")
                && !lower.equals("ana sayfa");
    }

    private boolean isValidNewsUrl(String url) {
        if (isBlank(url)) {
            return false;
        }

        if (!url.startsWith(BASE_URL)) {
            return false;
        }

        String lower = url.toLowerCase(Locale.ROOT);

        // çok genel veya alakasız sayfaları ele
        if (lower.equals(BASE_URL)
                || lower.equals(BASE_URL + "/")
                || lower.contains("/video")
                || lower.contains("/canli-yayin")
                || lower.contains("/yazarlar")
                || lower.contains("/kategoriler")
                || lower.contains("/arsiv")
                || lower.contains("/arama")
                || lower.contains("/iletisim")
                || lower.contains("/kullanici")
                || lower.contains("/uye-giris")) {
            return false;
        }

        // haber olma ihtimali yüksek pattern'ler
        return lower.contains("/haber")
                || lower.contains("/haberler/")
                || lower.contains("/ekonomi/")
                || lower.contains("/piyasalar/")
                || lower.matches(".*/[a-z0-9-]+-[0-9]+$");
    }

    private String extractTitle(Element anchor) {
        Element card = closestMeaningfulContainer(anchor);

        String[] selectors = {
                "h1", "h2", "h3",
                ".title",
                ".headline",
                ".spot-title",
                ".news-title",
                ".card-title"
        };

        for (String selector : selectors) {
            Element found = card.selectFirst(selector);
            if (found != null && !isBlank(found.text())) {
                return found.text();
            }
        }

        if (!isBlank(anchor.attr("title"))) {
            return anchor.attr("title");
        }

        return anchor.text();
    }

    private String extractSummary(Element anchor) {
        Element card = closestMeaningfulContainer(anchor);

        String[] selectors = {
                ".summary",
                ".spot",
                ".description",
                ".news-summary",
                ".card-text",
                "p"
        };

        for (String selector : selectors) {
            Element found = card.selectFirst(selector);
            if (found != null && !isBlank(found.text())) {
                String text = found.text().trim();

                // başlık ile tamamen aynıysa summary sayma
                String title = cleanText(extractTitle(anchor));
                if (!text.equalsIgnoreCase(title)) {
                    return text;
                }
            }
        }

        return null;
    }

    private String extractCategory(Element anchor) {
        Element card = closestMeaningfulContainer(anchor);

        String[] selectors = {
                ".category",
                ".tag",
                ".label",
                ".news-category",
                ".card-category"
        };

        for (String selector : selectors) {
            Element found = card.selectFirst(selector);
            if (found != null && !isBlank(found.text())) {
                return found.text();
            }
        }

        String url = normalizeUrl(anchor.absUrl("href")).toLowerCase(Locale.ROOT);
        if (url.contains("/ekonomi/")) {
            return "Ekonomi";
        }
        if (url.contains("/piyasalar/")) {
            return "Piyasalar";
        }
        if (url.contains("/haberler/")) {
            return "Haberler";
        }

        return "Bloomberg HT";
    }

    private LocalDateTime extractPublishedAt(Element anchor) {
        Element card = closestMeaningfulContainer(anchor);

        String[] selectors = {
                "time[datetime]",
                "time",
                ".date",
                ".news-date",
                ".publish-date",
                ".time"
        };

        for (String selector : selectors) {
            Element found = card.selectFirst(selector);
            if (found == null) {
                continue;
            }

            String raw = selector.equals("time[datetime]")
                    ? found.attr("datetime")
                    : found.text();

            LocalDateTime parsed = tryParseDate(raw);
            if (parsed != null) {
                return parsed;
            }
        }

        return null;
    }

    private Element closestMeaningfulContainer(Element anchor) {
        Element current = anchor;

        for (int i = 0; i < 4 && current.parent() != null; i++) {
            current = current.parent();

            String className = current.className().toLowerCase(Locale.ROOT);
            String tagName = current.tagName().toLowerCase(Locale.ROOT);

            if (tagName.equals("article")
                    || className.contains("news")
                    || className.contains("haber")
                    || className.contains("card")
                    || className.contains("item")
                    || className.contains("content")) {
                return current;
            }
        }

        return anchor.parent() != null ? anchor.parent() : anchor;
    }

    private LocalDateTime tryParseDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr-TR")),
                DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.ISO_DATE_TIME) {
                    return OffsetDateTime.parse(raw, formatter)
                            .atZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDateTime();
                }
                return LocalDateTime.parse(raw, formatter);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private String normalizeUrl(String url) {
        if (isBlank(url)) {
            return null;
        }

        String normalized = url.trim();

        if (normalized.startsWith("/")) {
            normalized = BASE_URL + normalized;
        }

        // tracking parametreleri azalt
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }

        return normalized;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String buildExternalId(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return "BLOOMBERG_HT-" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "BLOOMBERG_HT-" + Math.abs(url.hashCode());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}