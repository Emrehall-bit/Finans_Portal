package com.emrehalli.financeportal.news.provider.bloomberght.mapper;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsRegionScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BloombergHtNewsMapper {

    private static final String BASE_URL = "https://www.bloomberght.com";
    private static final Logger logger = LogManager.getLogger(BloombergHtNewsMapper.class);
    private static final int MAX_DATE_WARN_LOGS = 10;
    private static final Pattern DATE_TIME_DOTTED_PATTERN = Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})(?:\\s+(\\d{1,2}):(\\d{2}))?$");
    private static final Pattern DATE_TIME_SLASH_PATTERN = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})(?:\\s+(\\d{1,2}):(\\d{2}))?$");
    private static final Pattern TURKISH_DATE_PATTERN = Pattern.compile(
            "^(\\d{1,2})\\s+([\\p{L}]+)\\s+(\\d{4})(?:\\s+([\\p{L}]+))?(?:\\s+(\\d{1,2}):(\\d{2}))?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern TIME_ONLY_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern RELATIVE_DATE_PATTERN = Pattern.compile("^(bugun|dun)(?:\\s+(\\d{1,2}):(\\d{2}))?$");
    private static final Map<String, Integer> TURKISH_MONTHS = createTurkishMonthMap();

    public List<NewsItemDto> map(Document document) {
        return mapWithReport(document).items();
    }

    public ParseReport mapWithReport(Document document) {
        if (document == null) {
            return new ParseReport(List.of(), 0, 0, 0);
        }

        List<Element> candidates = collectCandidateAnchors(document);
        List<NewsItemDto> mapped = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        int invalidCandidateCount = 0;
        int dateWarnCount = 0;

        for (Element candidate : candidates) {
            String url = normalizeUrl(candidate.absUrl("href"));
            if (!isValidNewsUrl(url) || !seenUrls.add(url)) {
                invalidCandidateCount++;
                continue;
            }

            String title = extractTitle(candidate);
            if (isBlank(title) || title.length() < 15) {
                invalidCandidateCount++;
                continue;
            }

            String summary = extractSummary(candidate);
            String category = extractCategory(candidate);
            PublishedAtParseResult publishedAtResult = extractPublishedAt(candidate);
            LocalDateTime publishedAt = publishedAtResult.value();

            if (publishedAt == null && !isBlank(publishedAtResult.rawValue()) && dateWarnCount < MAX_DATE_WARN_LOGS) {
                logger.warn(
                        "Failed to parse Bloomberg HT publishedAt. title: {}, source: {}, dateRaw: {}",
                        cleanText(title),
                        "Bloomberg HT",
                        publishedAtResult.rawValue()
                );
                dateWarnCount++;
            }

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
                            .publishedAt(publishedAt)
                            .build()
            );
        }

        return new ParseReport(mapped, candidates.size(), seenUrls.size(), invalidCandidateCount);
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

    private PublishedAtParseResult extractPublishedAt(Element anchor) {
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
                return new PublishedAtParseResult(parsed, raw);
            }

            if (!isBlank(raw)) {
                return new PublishedAtParseResult(null, raw);
            }
        }

        return new PublishedAtParseResult(null, null);
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

        String normalized = normalizeDateText(raw);
        LocalDateTime relativeDate = parseRelativeDate(normalized);
        if (relativeDate != null) {
            return relativeDate;
        }

        LocalDateTime timeOnlyDate = parseTimeOnly(normalized);
        if (timeOnlyDate != null) {
            return timeOnlyDate;
        }

        LocalDateTime dottedDate = parsePattern(normalized, DATE_TIME_DOTTED_PATTERN);
        if (dottedDate != null) {
            return dottedDate;
        }

        LocalDateTime slashDate = parsePattern(normalized, DATE_TIME_SLASH_PATTERN);
        if (slashDate != null) {
            return slashDate;
        }

        LocalDateTime turkishDate = parseTurkishDate(normalized);
        if (turkishDate != null) {
            return turkishDate;
        }

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
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

    private LocalDateTime parseRelativeDate(String raw) {
        String normalized = normalizeTurkishToken(raw);
        Matcher matcher = RELATIVE_DATE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        LocalDate baseDate = "dun".equals(matcher.group(1)) ? LocalDate.now().minusDays(1) : LocalDate.now();
        int hour = matcher.group(2) != null ? parseHour(matcher.group(2)) : 0;
        int minute = matcher.group(3) != null ? parseMinute(matcher.group(3)) : 0;
        return safeDateTime(baseDate.getYear(), baseDate.getMonthValue(), baseDate.getDayOfMonth(), hour, minute);
    }

    private LocalDateTime parseTimeOnly(String raw) {
        Matcher matcher = TIME_ONLY_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }

        return LocalDate.now().atTime(parseHour(matcher.group(1)), parseMinute(matcher.group(2)));
    }

    private LocalDateTime parsePattern(String raw, Pattern pattern) {
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }

        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int year = Integer.parseInt(matcher.group(3));
        int hour = matcher.group(4) != null ? parseHour(matcher.group(4)) : 0;
        int minute = matcher.group(5) != null ? parseMinute(matcher.group(5)) : 0;
        return safeDateTime(year, month, day, hour, minute);
    }

    private LocalDateTime parseTurkishDate(String raw) {
        Matcher matcher = TURKISH_DATE_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }

        int day = Integer.parseInt(matcher.group(1));
        Integer month = TURKISH_MONTHS.get(normalizeTurkishToken(matcher.group(2)));
        if (month == null) {
            return null;
        }

        int year = Integer.parseInt(matcher.group(3));
        int hour = matcher.group(5) != null ? parseHour(matcher.group(5)) : 0;
        int minute = matcher.group(6) != null ? parseMinute(matcher.group(6)) : 0;
        return safeDateTime(year, month, day, hour, minute);
    }

    private LocalDateTime safeDateTime(int year, int month, int day, int hour, int minute) {
        try {
            return LocalDate.of(year, month, day).atTime(hour, minute);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int parseHour(String rawHour) {
        return Integer.parseInt(rawHour);
    }

    private int parseMinute(String rawMinute) {
        return Integer.parseInt(rawMinute);
    }

    private String normalizeDateText(String raw) {
        String normalized = cleanText(raw);
        if (normalized == null) {
            return null;
        }

        return normalized
                .replace(",", " ")
                .replace(" - ", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeTurkishToken(String value) {
        if (value == null) {
            return null;
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u');
    }

    private static Map<String, Integer> createTurkishMonthMap() {
        Map<String, Integer> months = new HashMap<>();
        months.put("ocak", 1);
        months.put("subat", 2);
        months.put("mart", 3);
        months.put("nisan", 4);
        months.put("mayis", 5);
        months.put("haziran", 6);
        months.put("temmuz", 7);
        months.put("agustos", 8);
        months.put("eylul", 9);
        months.put("ekim", 10);
        months.put("kasim", 11);
        months.put("aralik", 12);
        return Map.copyOf(months);
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

    public record ParseReport(List<NewsItemDto> items, int candidateCount, int uniqueUrlCount, int invalidCandidateCount) {
    }

    private record PublishedAtParseResult(LocalDateTime value, String rawValue) {
    }
}
