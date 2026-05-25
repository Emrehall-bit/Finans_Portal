package com.emrehalli.financeportal.news.provider.kap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.jsoup.select.Elements;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class KapNewsClient {

    private static final Logger logger = LogManager.getLogger(KapNewsClient.class);

    private static final DateTimeFormatter KAP_REQUEST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter KAP_PUBLISH_DATE_FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter KAP_PUBLISH_DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter KAP_PUBLISH_DATE_DATE_ONLY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final int DETAIL_PARSE_MIN_LENGTH = 80;
    private static final Set<String> NOISE_BUTTON_LABELS = Set.of(
            "PDF", "WORD", "EXCEL", "YAZDIR", "A+", "A-", "İMZALI GÖRÜNTÜLE", "IMZALI GORUNTULE", "PRINT"
    );
    // Internal KAP field prefix — these are system identifiers, not user-visible labels
    private static final Pattern NOISE_FIELD_PATTERN = Pattern.compile("(?i)^oda[_\\s]");
    // Values that are structurally empty (null-equivalent, empty arrays/objects, punctuation-only)
    private static final Pattern NOISE_VALUE_PATTERN = Pattern.compile("^[\\[\\]{}\\s,;|/*:.-]*$");

    private record KapDetailContent(String text, String html, String sectionsJson) {}
    private record RowPair(String label, String value) {}

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

            KapDetailContent detail = null;
            if (properties.isFetchDetailEnabled() && basic.getDisclosureIndex() != null) {
                detail = fetchDisclosureDetail(basic.getDisclosureIndex());
                if (detail == null) {
                    detailParseFailed++;
                }
            }

            NewsItemDto dto = toNewsItemDto(basic, detail);
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

    public KapDetailContent fetchDisclosureDetail(Long disclosureIndex) {
        if (disclosureIndex == null) return null;

        String url = properties.normalizedBaseUrl() + "/tr/Bildirim/" + disclosureIndex;
        String html;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildBrowserHeaders()), String.class
            );
            html = response.getBody();
        } catch (Exception ex) {
            logger.debug("KAP detail HTTP error. disclosureIndex: {}, reason: {}", disclosureIndex, ex.getMessage());
            return null;
        }

        if (html == null || html.isBlank()) {
            logger.debug("KAP detail empty response. disclosureIndex: {}", disclosureIndex);
            return null;
        }

        return parseKapDetailContent(html, url, disclosureIndex);
    }

    private KapDetailContent parseKapDetailContent(String html, String url, Long disclosureIndex) {
        Document doc = Jsoup.parse(html, url);

        // Remove KAP site chrome — navigation, sidebars, toolbars, resize controls
        doc.select("nav, header, footer, script, style, noscript").remove();
        doc.select("[class*='sidebar'], [id*='sidebar'], [class*='side-menu'], [id*='side-menu']").remove();
        doc.select("[class*='font-resize'], [class*='text-resize'], [class*='fontsize']").remove();
        doc.select("button, a").forEach(el -> {
            if (NOISE_BUTTON_LABELS.contains(el.text().trim().toUpperCase(Locale.ROOT))) el.remove();
        });
        // Remove English-language duplicates before any text extraction
        doc.select(".content-en, [lang='en']").remove();

        ArrayNode sections = objectMapper.createArrayNode();
        Set<String> seenNormalized = new LinkedHashSet<>();
        StringBuilder textBuilder = new StringBuilder();

        parseTaxonomySections(doc, sections, seenNormalized, textBuilder);
        parseTextBlocks(doc, sections, seenNormalized, textBuilder);
        parseFinancialTables(doc, sections, textBuilder);
        parseBeyan(doc, sections, seenNormalized, textBuilder);

        String rawText = textBuilder.toString().trim();
        boolean hasContent = !sections.isEmpty() && rawText.length() > DETAIL_PARSE_MIN_LENGTH;

        if (!hasContent) {
            logger.info("KAP detail parse. disclosureIndex: {}, textLength: {}, sectionsCount: {}, parseSuccess: false",
                    disclosureIndex, rawText.length(), sections.size());
            return null;
        }

        String finalText = normalizeDetailText(rawText);
        String sectionsJson = null;
        try {
            sectionsJson = objectMapper.writeValueAsString(sections);
        } catch (Exception ex) {
            logger.warn("KAP sections JSON serialization failed. disclosureIndex: {}, reason: {}", disclosureIndex, ex.getMessage());
        }

        logger.info("KAP detail parse. disclosureIndex: {}, textLength: {}, sectionsCount: {}, parseSuccess: true",
                disclosureIndex, finalText.length(), sections.size());

        return new KapDetailContent(finalText, null, sectionsJson);
    }

    private List<String> splitToParagraphs(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.split("\\n{2,}")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) result.add(trimmed);
        }
        return result;
    }

    private String resolveTableTitle(Element table) {
        Element caption = table.selectFirst("caption");
        if (caption != null && !caption.text().isBlank()) return caption.text().trim();
        Element prev = table.previousElementSibling();
        while (prev != null) {
            if (prev.tagName().matches("h[1-6]") && !prev.text().isBlank()) return prev.text().trim();
            if (!prev.text().isBlank()) break;
            prev = prev.previousElementSibling();
        }
        return "Finansal Tablo";
    }

    private String normalizeForDedup(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDetailText(String text) {
        if (text == null || text.isBlank()) return "";
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[^\\S\n]+", " ")
                .replaceAll("[ \t]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private void parseTaxonomySections(Document doc, ArrayNode sections, Set<String> seenNormalized, StringBuilder textBuilder) {
        Elements tables = doc.select("table:has(.taxonomy-field-title)");
        if (tables.isEmpty()) {
            parseFlatTaxonomy(doc, sections, seenNormalized, textBuilder);
            return;
        }

        boolean firstGroup = true;
        String currentGroupTitle = null;
        List<RowPair> currentGroupRows = new ArrayList<>();

        for (Element table : tables) {
            for (Element row : table.select("tr")) {
                Elements cells = row.select("td, th");
                if (cells.isEmpty()) continue;

                if (isGroupHeaderRow(cells)) {
                    if (!currentGroupRows.isEmpty()) {
                        addRowSection(sections, firstGroup ? "ozet" : "metadata", currentGroupTitle, currentGroupRows, textBuilder);
                        currentGroupRows = new ArrayList<>();
                        firstGroup = false;
                    }
                    currentGroupTitle = extractGroupTitle(cells);
                    continue;
                }

                if (cells.size() < 2) continue;
                String label = extractCellText(cells.first());
                String value = extractCellText(cells.get(1));

                if (isNoiseField(label, value)) continue;
                String normLabel = normalizeForDedup(label);
                if (!seenNormalized.add(normLabel)) continue;

                String normValue = normalizeForDedup(value);
                if (normValue.length() > 10) seenNormalized.add(normValue);

                currentGroupRows.add(new RowPair(label, value));
            }
        }

        if (!currentGroupRows.isEmpty()) {
            addRowSection(sections, firstGroup ? "ozet" : "metadata", currentGroupTitle, currentGroupRows, textBuilder);
        }
    }

    private void parseFlatTaxonomy(Document doc, ArrayNode sections, Set<String> seenNormalized, StringBuilder textBuilder) {
        Elements labels = doc.select(".taxonomy-field-title .content-tr");
        Elements values = doc.select(".taxonomy-context-value .content-tr");

        int count = Math.min(labels.size(), values.size());
        if (count == 0) return;

        List<RowPair> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String label = labels.get(i).text().trim();
            String value = values.get(i).text().trim();

            if (isNoiseField(label, value)) continue;
            String normLabel = normalizeForDedup(label);
            if (!seenNormalized.add(normLabel)) continue;

            String normValue = normalizeForDedup(value);
            if (normValue.length() > 10) seenNormalized.add(normValue);

            rows.add(new RowPair(label, value));
        }

        addRowSection(sections, "ozet", null, rows, textBuilder);
    }

    private void parseTextBlocks(Document doc, ArrayNode sections, Set<String> seenNormalized, StringBuilder textBuilder) {
        Elements textBlocks = doc.select(".text-block-value");
        if (textBlocks.isEmpty()) {
            textBlocks = doc.select(".disclosureSummary");
        }

        for (Element block : textBlocks) {
            String rawText = block.text().trim();
            if (rawText.isBlank()) continue;

            List<String> paragraphs = splitToParagraphs(rawText);
            if (paragraphs.isEmpty()) continue;

            List<String> freshParagraphs = new ArrayList<>();
            for (String para : paragraphs) {
                String normPara = normalizeForDedup(para);
                if (normPara.length() > 20 && seenNormalized.contains(normPara)) continue;
                freshParagraphs.add(para);
            }

            if (freshParagraphs.isEmpty()) continue;

            freshParagraphs.forEach(para -> seenNormalized.add(normalizeForDedup(para)));

            ObjectNode section = objectMapper.createObjectNode();
            section.put("type", "aciklama");
            ArrayNode parasNode = objectMapper.createArrayNode();
            for (String para : freshParagraphs) {
                parasNode.add(para);
                textBuilder.append(para).append("\n\n");
            }
            section.set("paragraphs", parasNode);
            sections.add(section);
        }
    }

    private void parseFinancialTables(Document doc, ArrayNode sections, StringBuilder textBuilder) {
        Elements tables = doc.select("table.financial-table");

        for (Element table : tables) {
            String title = resolveTableTitle(table);

            List<String> headers = new ArrayList<>();
            Element thead = table.selectFirst("thead");
            if (thead != null) {
                Element headerRow = thead.selectFirst("tr");
                if (headerRow != null) {
                    for (Element cell : headerRow.select("th, td")) {
                        headers.add(cell.text().trim());
                    }
                }
            }

            List<List<String>> rows = new ArrayList<>();
            Element tbody = table.selectFirst("tbody");
            Elements rowElements = tbody != null ? tbody.select("tr") : table.select("tr");
            for (Element row : rowElements) {
                List<String> cells = new ArrayList<>();
                for (Element cell : row.select("td, th")) {
                    cells.add(cell.text().trim());
                }
                if (!cells.isEmpty() && cells.stream().anyMatch(c -> !c.isBlank())) {
                    rows.add(cells);
                }
            }

            if (rows.isEmpty()) continue;

            int colCount = headers.isEmpty()
                    ? rows.stream().mapToInt(List::size).max().orElse(0)
                    : headers.size();

            if (colCount < 2) {
                List<RowPair> pairs = new ArrayList<>();
                for (List<String> row : rows) {
                    String label = row.isEmpty() ? "" : row.get(0);
                    String value = row.size() > 1 ? row.get(1) : "";
                    if (!label.isBlank()) pairs.add(new RowPair(label, value));
                }
                addRowSection(sections, "ozet", title, pairs, textBuilder);
            } else {
                ObjectNode section = objectMapper.createObjectNode();
                section.put("type", "tablo");
                section.put("title", title);

                if (!headers.isEmpty()) {
                    ArrayNode headersNode = objectMapper.createArrayNode();
                    headers.forEach(headersNode::add);
                    section.set("headers", headersNode);
                }

                ArrayNode rowsNode = objectMapper.createArrayNode();
                for (List<String> row : rows) {
                    ArrayNode rowNode = objectMapper.createArrayNode();
                    row.forEach(rowNode::add);
                    rowsNode.add(rowNode);
                    textBuilder.append(String.join(" | ", row)).append("\n");
                }
                section.set("rows", rowsNode);
                sections.add(section);
                textBuilder.append("\n");
            }
        }
    }

    private void parseBeyan(Document doc, ArrayNode sections, Set<String> seenNormalized, StringBuilder textBuilder) {
        Elements beyanElements = doc.select(".notification_detail-disclosure-detail");

        for (Element el : beyanElements) {
            String text = el.text().trim();
            if (text.isBlank()) continue;

            String normText = normalizeForDedup(text);
            if (!seenNormalized.add(normText)) continue;

            ObjectNode section = objectMapper.createObjectNode();
            section.put("type", "beyan");
            section.put("title", "Beyan");
            section.put("content", text);
            sections.add(section);
            textBuilder.append(text).append("\n\n");
        }
    }

    private void addRowSection(ArrayNode sections, String type, String title, List<RowPair> rows, StringBuilder textBuilder) {
        if (rows.isEmpty()) return;

        ObjectNode section = objectMapper.createObjectNode();
        section.put("type", type);
        if (title != null && !title.isBlank()) section.put("title", title);

        ArrayNode rowsNode = objectMapper.createArrayNode();
        for (RowPair pair : rows) {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("label", pair.label());
            row.put("value", pair.value());
            rowsNode.add(row);
            textBuilder.append(pair.label()).append(": ").append(pair.value()).append("\n");
        }
        section.set("rows", rowsNode);
        sections.add(section);
        textBuilder.append("\n");
    }

    private boolean isGroupHeaderRow(Elements cells) {
        if (cells.size() != 1) return false;
        String colspan = cells.first().attr("colspan");
        if (colspan.isBlank()) return false;
        try {
            return Integer.parseInt(colspan.trim()) >= 2;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String extractGroupTitle(Elements cells) {
        if (cells.isEmpty()) return "";
        return cells.first().text().trim();
    }

    private String extractCellText(Element cell) {
        Element trChild = cell.selectFirst(".content-tr");
        if (trChild != null && !trChild.text().isBlank()) {
            return trChild.text().trim();
        }
        return cell.text().trim();
    }

    private boolean isNoiseField(String label, String value) {
        if (label == null || label.isBlank()) return true;
        if (NOISE_FIELD_PATTERN.matcher(label).find()) return true;
        String normValue = value != null ? value.trim() : "";
        if (NOISE_VALUE_PATTERN.matcher(normValue).matches()) return true;
        String trimmedLabel = label.trim();
        if (trimmedLabel.length() == 1 && !Character.isLetterOrDigit(trimmedLabel.charAt(0))) return true;
        String lv = normValue.toLowerCase(Locale.ROOT);
        return lv.equals("-") || lv.equals("--") || lv.equals("none") || lv.equals("null");
    }

    private NewsItemDto toNewsItemDto(KapDisclosureBasic basic, KapDetailContent detail) {
        String externalId = resolveExternalId(basic);
        if (externalId == null) {
            return null;
        }

        String url = resolveUrl(basic);
        String title = resolveTitle(basic);
        if (title == null) {
            return null;
        }

        String contentSections = detail != null ? detail.sectionsJson() : null;
        String summary = resolveSummary(basic);
        LocalDateTime publishedAt = parsePublishDate(basic.getPublishDate());
        String category = mapCategory(basic.getDisclosureClass(), basic.getDisclosureType());
        String qualityStatus = NewsQualityStatus.KAP_DISCLOSURE.name();

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
                .contentSections(contentSections)
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

    private String resolveSummary(KapDisclosureBasic basic) {
        if (hasText(basic.getSummary())) return basic.getSummary().trim();
        if (hasText(basic.getTitle())) return basic.getTitle().trim();
        if (hasText(basic.getCompanyTitle())) return basic.getCompanyTitle().trim();
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
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        headers.set("Accept-Language", "tr-TR,tr;q=0.9");
        headers.set("Referer", properties.normalizedBaseUrl() + "/tr");
        return headers;
    }
}



