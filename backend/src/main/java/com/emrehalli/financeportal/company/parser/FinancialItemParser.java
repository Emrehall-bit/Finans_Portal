package com.emrehalli.financeportal.company.parser;

import com.emrehalli.financeportal.company.dto.ParseFailureDto;
import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.provider.kap.KapFinancialTableClient;
import com.emrehalli.financeportal.company.provider.kap.KapFinancialTableClient.KapFinancialTableResult;
import com.emrehalli.financeportal.company.provider.kap.KapFinancialTableClient.ParsedFinancialItem;
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
    private static final int PREVIEW_LENGTH = 300;
    private static final String REASON_HTTP_FETCH_FAILED = "HTTP fetch failed";
    private static final String REASON_HTML_NO_ROWS = "HTML fetched but no table rows found";
    private static final String REASON_NO_ALIAS_MATCH = "Table rows found but no dictionary alias matched";
    private static final String REASON_DISCLOSURE_DETAIL_PAGE = "Source URL is disclosure detail page, financial table endpoint required";
    private static final String REASON_UNKNOWN_PARSE_ERROR = "Unknown parse error";
    private static final int REQUIRED_XLSX_ITEM_COUNT = 3;

    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final FinancialItemDictionary dictionary;
    private final RestTemplate restTemplate;
    private final KapFinancialTableClient financialTableClient;

    public FinancialItemParser(CompanyFinancialReportRepository reportRepository,
                               CompanyFinancialValueRepository valueRepository,
                               FinancialItemDictionary dictionary,
                               RestTemplate restTemplate,
                               KapFinancialTableClient financialTableClient) {
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.dictionary = dictionary;
        this.restTemplate = restTemplate;
        this.financialTableClient = financialTableClient;
    }

    /** Parse only PENDING reports (default, backwards-compatible). */
    @Transactional
    public ParseAttemptResult parsePendingReport(Long reportId) {
        return parseReport(reportId, false);
    }

    /** Parse a report. When force=true, re-parses FAILED reports too. */
    @Transactional
    public ParseAttemptResult parseReport(Long reportId, boolean force) {
        CompanyFinancialReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            logger.warn("Report not found. reportId={}", reportId);
            return ParseAttemptResult.failed(ParseFailureDto.builder()
                    .reportId(reportId)
                    .reason("Report not found")
                    .build());
        }

        if (!force && report.getParseStatus() != ParseStatus.PENDING) {
            logger.debug("Report not PENDING, skipping. reportId={}, status={}", reportId, report.getParseStatus());
            return ParseAttemptResult.ok(report.getParseStatus());
        }

        ParseFailureDto validationFailure = validateXlsxRequest(report);
        if (validationFailure != null) {
            applyStatus(report, ParseStatus.FAILED);
            logXlsxParseResult(report, null, 0, 0, ParseStatus.FAILED);
            return ParseAttemptResult.failed(validationFailure);
        }

        KapFinancialTableResult tableResult;
        try {
            tableResult = financialTableClient.fetchFinancialTable(report);
        } catch (Exception e) {
            String contentType = null;
            String responsePreview = e.getMessage();
            if (e instanceof org.springframework.web.client.RestClientResponseException responseException) {
                contentType = responseException.getResponseHeaders() != null
                        ? responseException.getResponseHeaders().getFirst("Content-Type")
                        : null;
                responsePreview = preview(responseException.getResponseBodyAsString());
            }
            logger.warn("KAP compareItems XLSX fetch/parse failed. reportId={}, year={}, quarter={}, error={}",
                    reportId, report.getPeriodYear(), report.getPeriodQuarter(), e.getMessage());
            applyStatus(report, ParseStatus.FAILED);
            logXlsxParseResult(report, null, 0, 0, ParseStatus.FAILED);
            return ParseAttemptResult.failed(ParseFailureDto.builder()
                    .reportId(reportId)
                    .sourceUrl(report.getSourceUrl())
                    .periodYear(report.getPeriodYear())
                    .periodQuarter(report.getPeriodQuarter())
                    .reason(classifyXlsxException(e))
                    .contentType(contentType)
                    .fetchedTextPreview(responsePreview)
                    .build());
        }

        int matchedCount = tableResult.items().size();
        int savedCount = 0;
        int updatedCount = 0;
        try {
            for (ParsedFinancialItem item : tableResult.items().values()) {
                if (saveOrUpdateValue(report, item)) {
                    updatedCount++;
                }
                savedCount++;
            }
        } catch (Exception e) {
            logger.warn("Unknown parse error while saving XLSX values. reportId={}, url={}, error={}",
                    reportId, report.getSourceUrl(), e.getMessage());
            applyStatus(report, ParseStatus.FAILED);
            logXlsxParseResult(report, tableResult, matchedCount, savedCount, ParseStatus.FAILED);
            return ParseAttemptResult.failed(ParseFailureDto.builder()
                    .reportId(reportId)
                    .sourceUrl(report.getSourceUrl())
                    .periodYear(report.getPeriodYear())
                    .periodQuarter(report.getPeriodQuarter())
                    .reason(REASON_UNKNOWN_PARSE_ERROR)
                    .contentType(tableResult.contentType())
                    .fetchedTextPreview(tableResult.preview())
                    .selectedSheetName(tableResult.sheetName())
                    .workbookSheetNames(String.join(", ", tableResult.workbookSheetNames()))
                    .rowPreviewFirst20(tableResult.rowPreviewFirst20())
                    .build(), matchedCount, savedCount, updatedCount);
        }

        ParseStatus status = determineXlsxStatus(matchedCount);
        applyStatus(report, status);
        logXlsxParseResult(report, tableResult, matchedCount, savedCount, status);

        if (status == ParseStatus.FAILED) {
            return ParseAttemptResult.failed(ParseFailureDto.builder()
                    .reportId(reportId)
                    .sourceUrl(report.getSourceUrl())
                    .periodYear(report.getPeriodYear())
                    .periodQuarter(report.getPeriodQuarter())
                    .reason("KAP compareItems XLSX parsed but none of required MVP items matched: HASILAT, NET_DONEM_KARI, OZKAYNAKLAR")
                    .contentType(tableResult.contentType())
                    .fetchedTextPreview(tableResult.preview())
                    .selectedSheetName(tableResult.sheetName())
                    .workbookSheetNames(String.join(", ", tableResult.workbookSheetNames()))
                    .rowPreviewFirst20(tableResult.rowPreviewFirst20())
                    .build(), matchedCount, savedCount, updatedCount);
        }
        return ParseAttemptResult.ok(status, matchedCount, savedCount, updatedCount);
    }

    private ParseFailureDto validateXlsxRequest(CompanyFinancialReport report) {
        if (report.getCompany() == null) {
            return baseFailure(report, "Company profile is missing");
        }
        if (report.getCompany().getKapCompanyId() == null || report.getCompany().getKapCompanyId().isBlank()) {
            return baseFailure(report, "KAP member id missing");
        }
        if (report.getCompany().getCompanyName() == null || report.getCompany().getCompanyName().isBlank()) {
            return baseFailure(report, "Company companyName is missing");
        }
        if (report.getPeriodYear() == null) {
            return baseFailure(report, "Report periodYear is missing");
        }
        if (report.getPeriodQuarter() == null) {
            return baseFailure(report, "Report periodQuarter is missing");
        }
        return null;
    }

    private ParseFailureDto baseFailure(CompanyFinancialReport report, String reason) {
        return ParseFailureDto.builder()
                .reportId(report.getId())
                .sourceUrl(report.getSourceUrl())
                .periodYear(report.getPeriodYear())
                .periodQuarter(report.getPeriodQuarter())
                .reason(reason)
                .build();
    }

    private boolean saveOrUpdateValue(CompanyFinancialReport report, ParsedFinancialItem item) {
        Optional<CompanyFinancialValue> existing = valueRepository.findByReportIdAndItemKey(report.getId(), item.itemKey().name());
        CompanyFinancialValue value = existing
                .orElseGet(() -> CompanyFinancialValue.builder()
                        .report(report)
                        .itemKey(item.itemKey().name())
                        .createdAt(OffsetDateTime.now())
                        .build());
        value.setRawLabel(item.rawLabel());
        value.setValue(item.value());
        value.setCurrency(item.currency());
        value.setUnitMultiplier(item.unitMultiplier());
        value.setCurrentPeriod(true);
        valueRepository.save(value);
        return existing.isPresent();
    }

    private ParseStatus determineXlsxStatus(int matchedCount) {
        if (matchedCount >= REQUIRED_XLSX_ITEM_COUNT) {
            return ParseStatus.SUCCESS;
        }
        if (matchedCount > 0) {
            return ParseStatus.PARTIAL;
        }
        return ParseStatus.FAILED;
    }

    private String classifyXlsxException(Exception e) {
        if (e instanceof org.springframework.web.client.RestClientResponseException) {
            return REASON_HTTP_FETCH_FAILED;
        }
        if (e.getMessage() != null && e.getMessage().contains("XLSX parse failed")) {
            return REASON_UNKNOWN_PARSE_ERROR;
        }
        return REASON_UNKNOWN_PARSE_ERROR;
    }

    private void logXlsxParseResult(CompanyFinancialReport report,
                                    KapFinancialTableResult tableResult,
                                    int matchedCount,
                                    int savedCount,
                                    ParseStatus status) {
        logger.info("KAP financial XLSX parse result. reportId={}, year={}, quarter={}, xlsxByteSize={}, sheetName={}, workbookSheetNames={}, scannedRowCount={}, matchedItemCount={}, savedValueCount={}, finalParseStatus={}",
                report.getId(),
                report.getPeriodYear(),
                report.getPeriodQuarter(),
                tableResult != null ? tableResult.xlsxByteSize() : null,
                tableResult != null ? tableResult.sheetName() : null,
                tableResult != null ? tableResult.workbookSheetNames() : null,
                tableResult != null ? tableResult.scannedRowCount() : 0,
                matchedCount,
                savedCount,
                status);
    }

    // --- HTML fetch ---

    private FetchResult fetchHtml(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (compatible; FinansPortal/1.0)");
        headers.set("Accept", "text/html,application/xhtml+xml");
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String contentType = response.getHeaders().getFirst("Content-Type");
        return new FetchResult(
                response.getBody() != null ? response.getBody() : "",
                response.getStatusCode().value(),
                contentType);
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
        s = s.replaceAll("[()\\s]", "");
        if (s.isBlank() || "-".equals(s)) return Optional.empty();

        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else {
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

    private static String preview(String text) {
        if (text == null || text.isBlank()) return null;
        String stripped = Jsoup.parse(text).text();
        return stripped.length() > PREVIEW_LENGTH ? stripped.substring(0, PREVIEW_LENGTH) : stripped;
    }

    private static boolean isDisclosureDetailPage(String sourceUrl) {
        return sourceUrl != null && sourceUrl.contains("/tr/Bildirim/");
    }

    private FetchResult fetchFailure(Exception e) {
        if (e instanceof org.springframework.web.client.RestClientResponseException responseException) {
            String contentType = responseException.getResponseHeaders() != null
                    ? responseException.getResponseHeaders().getFirst("Content-Type")
                    : null;
            return new FetchResult(
                    responseException.getResponseBodyAsString(),
                    responseException.getStatusCode().value(),
                    contentType);
        }
        return null;
    }

    private void logReportParseResult(CompanyFinancialReport report,
                                      FetchResult fetch,
                                      String body,
                                      int rowCount,
                                      int matchedCount,
                                      ParseStatus parseStatus) {
        logger.info("Financial report parse result. reportId={}, sourceUrl={}, httpStatus={}, contentType={}, htmlLength={}, rowCount={}, matchedItemCount={}, parseStatus={}",
                report.getId(),
                report.getSourceUrl(),
                fetch != null ? fetch.httpStatus() : null,
                fetch != null ? fetch.contentType() : null,
                body != null ? body.length() : null,
                rowCount,
                matchedCount,
                parseStatus);
    }

    private record FetchResult(String body, int httpStatus, String contentType) {}
}
