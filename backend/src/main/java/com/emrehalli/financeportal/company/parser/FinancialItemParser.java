package com.emrehalli.financeportal.company.parser;

import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.repository.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.repository.CompanyFinancialValueRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Service
public class FinancialItemParser {

    private static final Logger logger = LogManager.getLogger(FinancialItemParser.class);
    private static final int TOTAL_ITEM_KEYS = FinancialItemKey.values().length;

    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final FinancialItemDictionary dictionary;
    private final RestTemplate restTemplate;

    public FinancialItemParser(CompanyFinancialReportRepository reportRepository,
                               CompanyFinancialValueRepository valueRepository,
                               FinancialItemDictionary dictionary,
                               RestTemplate restTemplate) {
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.dictionary = dictionary;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public ParseStatus parsePendingReport(Long reportId) {
        CompanyFinancialReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            logger.warn("Report not found. reportId={}", reportId);
            return ParseStatus.FAILED;
        }
        if (report.getParseStatus() != ParseStatus.PENDING) {
            logger.debug("Report not PENDING, skipping. reportId={}, status={}", reportId, report.getParseStatus());
            return report.getParseStatus();
        }
        if (report.getSourceUrl() == null || report.getSourceUrl().isBlank()) {
            logger.warn("Report has no sourceUrl. reportId={}", reportId);
            return applyStatus(report, ParseStatus.FAILED);
        }

        String html;
        try {
            html = fetchHtml(report.getSourceUrl());
        } catch (Exception e) {
            logger.warn("Failed to fetch report URL. reportId={}, url={}", reportId, report.getSourceUrl(), e);
            return applyStatus(report, ParseStatus.FAILED);
        }

        Map<FinancialItemKey, BigDecimal> parsed = parseHtml(html);

        if (parsed.isEmpty()) {
            logger.info("No items parsed. reportId={}", reportId);
            return applyStatus(report, ParseStatus.FAILED);
        }

        int savedCount = 0;
        for (Map.Entry<FinancialItemKey, BigDecimal> entry : parsed.entrySet()) {
            String itemKey = entry.getKey().name();
            if (valueRepository.existsByReportIdAndItemKey(reportId, itemKey)) {
                continue;
            }
            valueRepository.save(CompanyFinancialValue.builder()
                    .report(report)
                    .itemKey(itemKey)
                    .rawLabel(entry.getKey().name())
                    .value(entry.getValue())
                    .currency("TRY")
                    .unitMultiplier(1)
                    .currentPeriod(true)
                    .createdAt(OffsetDateTime.now())
                    .build());
            savedCount++;
        }

        ParseStatus status = parsed.size() >= TOTAL_ITEM_KEYS
                ? ParseStatus.SUCCESS
                : ParseStatus.PARTIAL;

        logger.info("Parse done. reportId={}, found={}/{}, status={}",
                reportId, parsed.size(), TOTAL_ITEM_KEYS, status);
        return applyStatus(report, status);
    }

    // --- HTML fetch ---

    private String fetchHtml(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (compatible; FinansPortal/1.0)");
        headers.set("Accept", "text/html,application/xhtml+xml");
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody() != null ? response.getBody() : "";
    }

    // --- HTML parsing ---

    Map<FinancialItemKey, BigDecimal> parseHtml(String html) {
        Map<FinancialItemKey, BigDecimal> results = new EnumMap<>(FinancialItemKey.class);
        if (html == null || html.isBlank()) return results;

        Document doc = Jsoup.parse(html);

        for (Element row : doc.select("tr")) {
            Elements cells = row.select("td, th");
            if (cells.size() < 2) continue;

            String label = cells.get(0).text();
            Optional<FinancialItemKey> key = dictionary.match(label);
            if (key.isEmpty() || results.containsKey(key.get())) continue;

            // Try cells 1, 2, 3 in order — first parseable non-zero value wins
            for (int i = 1; i < Math.min(cells.size(), 4); i++) {
                Optional<BigDecimal> value = parseDecimal(cells.get(i).text());
                if (value.isPresent()) {
                    results.put(key.get(), value.get());
                    break;
                }
            }
        }

        return results;
    }

    // --- Number parsing ---

    Optional<BigDecimal> parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String s = raw.trim();
        if ("-".equals(s) || "--".equals(s) || s.isEmpty()) return Optional.empty();

        boolean negative = s.startsWith("(") && s.endsWith(")");
        // Strip parentheses and any whitespace
        s = s.replaceAll("[()\\s]", "");
        if (s.isBlank() || "-".equals(s)) return Optional.empty();

        // Turkish format: dots = thousand separators, comma = decimal separator
        // e.g. "1.234.567,89" → "1234567.89"
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else {
            // No comma — dots are thousand separators (typical for integer financial figures)
            s = s.replace(".", "");
        }

        try {
            BigDecimal value = new BigDecimal(s);
            return Optional.of(negative ? value.negate() : value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // --- Helpers ---

    private ParseStatus applyStatus(CompanyFinancialReport report, ParseStatus status) {
        report.setParseStatus(status);
        report.setLastCheckedAt(OffsetDateTime.now());
        reportRepository.save(report);
        return status;
    }
}
