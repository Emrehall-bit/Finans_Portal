package com.emrehalli.financeportal.company.provider.kap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.company.dto.KapFinancialTableDebugResponse;
import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.entity.CompanyProfile;
import com.emrehalli.financeportal.company.parser.FinancialItemKey;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class KapFinancialTableClient {

    private static final Logger logger = LogManager.getLogger(KapFinancialTableClient.class);
    private static final String COMPARE_PAGE_URL = "https://www.kap.org.tr/tr/kalem-karsilastirma";
    private static final String COMPARE_ITEMS_URL = "https://www.kap.org.tr/tr/api/export/compareItems";
    private static final String ORIGIN = "https://www.kap.org.tr";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7";
    private static final String COMPARE_PAGE_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String COMPANY_TYPE = "IGS";
    private static final String SECTOR = "GENERAL";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final int PREVIEW_ROW_LIMIT = 8;
    private static final int FAILURE_PREVIEW_ROW_LIMIT = 20;
    private static final int PREVIEW_CELL_LIMIT = 8;
    private static final List<String> SHEET_SIGNAL_TERMS = List.of(
            "hasılat", "hasilat", "revenue", "özkaynak", "ozkaynak", "profit", "dönem", "donem"
    );

    private static final Map<String, FinancialItemKey> ITEM_KEY_BY_CODE = Map.of(
            "ifrs-full_Revenue", FinancialItemKey.HASILAT,
            "ifrs-full_ProfitLoss", FinancialItemKey.NET_DONEM_KARI,
            "ifrs-full_Equity", FinancialItemKey.OZKAYNAKLAR,
            "ifrs-full_Assets", FinancialItemKey.TOPLAM_VARLIKLAR,
            "ifrs-full_EquityAndLiabilities", FinancialItemKey.TOPLAM_KAYNAKLAR,
            "ifrs-full_GrossProfit", FinancialItemKey.BRUT_KAR,
            "ifrs-full_CurrentLiabilities", FinancialItemKey.KISA_VADELI_YUKUMLULUKLER,
            "ifrs-full_NoncurrentLiabilities", FinancialItemKey.UZUN_VADELI_YUKUMLULUKLER
    );

    private static final Map<FinancialItemKey, List<String>> ITEM_KEY_BY_LABEL = Map.of(
            FinancialItemKey.HASILAT, List.of("hasılat", "hasilat", "finans sektörü faaliyetleri hasılatı", "finans sektoru faaliyetleri hasilati", "satış gelirleri", "satis gelirleri", "revenue"),
            FinancialItemKey.NET_DONEM_KARI, List.of("net dönem karı", "net donem kari", "net dönem kârı", "profit loss"),
            FinancialItemKey.OZKAYNAKLAR, List.of("toplam özkaynaklar", "toplam ozkaynaklar", "özkaynaklar", "ozkaynaklar", "equity"),
            FinancialItemKey.TOPLAM_VARLIKLAR, List.of("toplam varlıklar", "toplam varliklar"),
            FinancialItemKey.TOPLAM_KAYNAKLAR, List.of("toplam kaynaklar"),
            FinancialItemKey.BRUT_KAR, List.of("brüt kar", "brut kar", "brüt kar zarar", "brut kar zarar", "brüt esas faaliyet karı", "brut esas faaliyet kari", "gross profit"),
            FinancialItemKey.KISA_VADELI_YUKUMLULUKLER, List.of("toplam kısa vadeli yükümlülükler", "toplam kisa vadeli yukumlulukler", "kısa vadeli yükümlülükler toplamı", "kisa vadeli yukumlulukler toplami", "kısa vadeli yükümlülükler", "kisa vadeli yukumlulukler"),
            FinancialItemKey.UZUN_VADELI_YUKUMLULUKLER, List.of("toplam uzun vadeli yükümlülükler", "toplam uzun vadeli yukumlulukler", "uzun vadeli yükümlülükler toplamı", "uzun vadeli yukumlulukler toplami", "uzun vadeli yükümlülükler", "uzun vadeli yukumlulukler")
    );

    private static final List<String> LEGACY_ITEM_CODES = List.of(
            "ifrs-full_EquityAndLiabilities",
            "ifrs-full_Assets",
            "ifrs-full_ProfitLoss",
            "ifrs-full_Equity",
            "ifrs-full_Revenue"
    );
    // 7-item set without GrossProfit — works for companies (e.g. defense) that don't report ifrs-full_GrossProfit
    private static final List<String> MINIMAL_ITEM_CODES = List.of(
            "ifrs-full_Revenue",
            "ifrs-full_ProfitLoss",
            "ifrs-full_Equity",
            "ifrs-full_Assets",
            "ifrs-full_EquityAndLiabilities",
            "ifrs-full_CurrentLiabilities",
            "ifrs-full_NoncurrentLiabilities"
    );
    private static final List<String> EXTENDED_ITEM_CODES = List.of(
            "ifrs-full_EquityAndLiabilities",
            "ifrs-full_Assets",
            "ifrs-full_CurrentLiabilities",
            "ifrs-full_NoncurrentLiabilities",
            "ifrs-full_GrossProfit",
            "ifrs-full_ProfitLoss",
            "ifrs-full_Equity",
            "ifrs-full_Revenue"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.forLanguageTag("tr-TR"));

    public KapFinancialTableClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public KapFinancialTableResult fetchForPeriod(CompanyProfile company, int year, int period) {
        Map<String, Object> payload = buildPayload(company, String.valueOf(year), String.valueOf(period), MINIMAL_ITEM_CODES);
        String rawJson = toJson(payload);
        String cookieHeader = startSession();
        logger.info("KAP compareItems backfill-period request. ticker={}, year={}, period={}, rawJson={}",
                company.getTickerCode(), year, period, rawJson);
        ResponseEntity<byte[]> response;
        try {
            response = restClient.post()
                    .uri(COMPARE_ITEMS_URL)
                    .headers(headers -> applyPostHeaders(headers, cookieHeader))
                    .body(rawJson)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException e) {
            logger.warn("KAP compareItems backfill-period POST failed. ticker={}, year={}, period={}, status={}, body={}",
                    company.getTickerCode(), year, period,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw e;
        }
        byte[] xlsx = response.getBody() != null ? response.getBody() : new byte[0];
        String contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString()
                : null;
        logger.info("KAP compareItems backfill-period response. ticker={}, year={}, period={}, status={}, contentType={}, xlsxByteSize={}",
                company.getTickerCode(), year, period,
                response.getStatusCode().value(), contentType, xlsx.length);
        return parseWorkbook(xlsx, contentType);
    }

    public KapFinancialTableResult fetchFinancialTable(CompanyFinancialReport report) {
        Map<String, Object> payload = buildPayload(
                report.getCompany(),
                report.getPeriodYear() != null ? String.valueOf(report.getPeriodYear()) : null,
                mapPeriod(report.getPeriodQuarter()),
                MINIMAL_ITEM_CODES);
        String rawJson = toJson(payload);
        String cookieHeader = startSession();
        logger.info("KAP compareItems request payload. reportId={}, rawJson={}, headerNames={}",
                report.getId(), rawJson, postHeaderNames());
        ResponseEntity<byte[]> response;
        try {
            response = restClient.post()
                    .uri(COMPARE_ITEMS_URL)
                    .headers(headers -> applyPostHeaders(headers, cookieHeader))
                    .body(rawJson)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException e) {
            logger.warn("KAP compareItems POST failed. status={}, contentType={}, responseBody={}",
                    e.getStatusCode().value(),
                    e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE) : null,
                    e.getResponseBodyAsString());
            if (!e.getStatusCode().is4xxClientError() && !isKapWrapped400(e)) {
                throw e;
            }
            payload = buildPayload(
                    report.getCompany(),
                    report.getPeriodYear() != null ? String.valueOf(report.getPeriodYear()) : null,
                    mapPeriod(report.getPeriodQuarter()),
                    MINIMAL_ITEM_CODES);
            rawJson = toJson(payload);
            logger.info("KAP compareItems retrying with minimal item list. reportId={}, rawJson={}", report.getId(), rawJson);
            response = restClient.post()
                    .uri(COMPARE_ITEMS_URL)
                    .headers(headers -> applyPostHeaders(headers, cookieHeader))
                    .body(rawJson)
                    .retrieve()
                    .toEntity(byte[].class);
        }

        byte[] xlsx = response.getBody() != null ? response.getBody() : new byte[0];
        String contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString()
                : null;
        logger.info("KAP compareItems POST response. status={}, contentType={}, xlsxByteSize={}",
                response.getStatusCode().value(), contentType, xlsx.length);
        return parseWorkbook(xlsx, contentType);
    }

    public KapFinancialBackfillTableResult fetchFinancialBackfill(CompanyProfile company, List<String> years, String period) {
        Map<String, Object> payload = buildPayload(company, years, period, MINIMAL_ITEM_CODES);
        String rawJson = toJson(payload);
        String cookieHeader = startSession();
        logger.info("KAP compareItems backfill request payload. ticker={}, rawJson={}, headerNames={}",
                company.getTickerCode(), rawJson, postHeaderNames());
        ResponseEntity<byte[]> response;
        try {
            response = restClient.post()
                    .uri(COMPARE_ITEMS_URL)
                    .headers(headers -> applyPostHeaders(headers, cookieHeader))
                    .body(rawJson)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException e) {
            logger.warn("KAP compareItems backfill POST failed. status={}, contentType={}, responseBody={}",
                    e.getStatusCode().value(),
                    e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE) : null,
                    e.getResponseBodyAsString());
            if (!e.getStatusCode().is4xxClientError() && !isKapWrapped400(e)) {
                throw e;
            }
            payload = buildPayload(company, years, period, MINIMAL_ITEM_CODES);
            rawJson = toJson(payload);
            logger.info("KAP compareItems backfill retrying with minimal item list. ticker={}, rawJson={}",
                    company.getTickerCode(), rawJson);
            response = restClient.post()
                    .uri(COMPARE_ITEMS_URL)
                    .headers(headers -> applyPostHeaders(headers, cookieHeader))
                    .body(rawJson)
                    .retrieve()
                    .toEntity(byte[].class);
        }

        byte[] xlsx = response.getBody() != null ? response.getBody() : new byte[0];
        String contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString()
                : null;
        logger.info("KAP compareItems backfill response. status={}, contentType={}, xlsxByteSize={}",
                response.getStatusCode().value(), contentType, xlsx.length);
        return parseBackfillWorkbook(xlsx, contentType);
    }

    public KapFinancialTableDebugResponse debugFetch(CompanyProfile company, String year, String period) {
        Map<String, Object> payload = buildPayload(company, year, period, MINIMAL_ITEM_CODES);
        String rawJson = toJson(payload);
        logger.info("KAP compareItems debug request payload. ticker={}, rawJson={}, headerNames={}",
                company.getTickerCode(), rawJson, postHeaderNames());
        try {
            String cookieHeader = startSession();
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(COMPARE_ITEMS_URL)
                    .headers(headers -> applyPostHeaders(headers, cookieHeader))
                    .body(rawJson)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] xlsx = response.getBody() != null ? response.getBody() : new byte[0];
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString()
                    : null;
            logger.info("KAP compareItems debug POST response. status={}, contentType={}, xlsxByteSize={}",
                    response.getStatusCode().value(), contentType, xlsx.length);
            return KapFinancialTableDebugResponse.builder()
                    .success(response.getStatusCode().is2xxSuccessful())
                    .httpStatus(response.getStatusCode().value())
                    .contentType(contentType)
                    .xlsxByteSize(xlsx.length)
                    .payload(payload)
                    .build();
        } catch (RestClientResponseException e) {
            String contentType = e.getResponseHeaders() != null
                    ? e.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE)
                    : null;
            logger.warn("KAP compareItems debug POST failed. status={}, contentType={}, responseBody={}",
                    e.getStatusCode().value(), contentType, e.getResponseBodyAsString());
            return KapFinancialTableDebugResponse.builder()
                    .success(false)
                    .httpStatus(e.getStatusCode().value())
                    .contentType(contentType)
                    .payload(payload)
                    .errorBody(e.getResponseBodyAsString())
                    .build();
        } catch (Exception e) {
            logger.warn("KAP compareItems debug fetch failed. error={}", e.getMessage());
            return KapFinancialTableDebugResponse.builder()
                    .success(false)
                    .payload(payload)
                    .errorBody(e.getMessage())
                    .build();
        }
    }

    private String startSession() {
        ResponseEntity<String> response = restClient.get()
                .uri(COMPARE_PAGE_URL)
                .header(HttpHeaders.ACCEPT, COMPARE_PAGE_ACCEPT)
                .header(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .header(HttpHeaders.REFERER, COMPARE_PAGE_URL)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .toEntity(String.class);

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        String cookieHeader = buildCookieHeader(setCookies);
        logger.info("KAP compareItems session GET. status={}, cookieNames={}",
                response.getStatusCode().value(), cookieNames(setCookies));
        return cookieHeader;
    }

    private void applyPostHeaders(HttpHeaders headers, String cookieHeader) {
        headers.set(HttpHeaders.ACCEPT, "*/*");
        headers.setAcceptLanguageAsLocales(List.of(Locale.forLanguageTag("tr-TR")));
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE);
        headers.setContentType(JSON);
        headers.set(HttpHeaders.ORIGIN, ORIGIN);
        headers.set(HttpHeaders.REFERER, COMPARE_PAGE_URL);
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set("sec-ch-ua", "\"Chromium\";v=\"148\", \"Google Chrome\";v=\"148\", \"Not=A?Brand\";v=\"99\"");
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"Windows\"");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            headers.set(HttpHeaders.COOKIE, cookieHeader);
        }
    }

    private List<String> postHeaderNames() {
        return List.of(
                HttpHeaders.ACCEPT,
                HttpHeaders.ACCEPT_LANGUAGE,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ORIGIN,
                HttpHeaders.REFERER,
                HttpHeaders.USER_AGENT,
                "sec-ch-ua",
                "sec-ch-ua-mobile",
                "sec-ch-ua-platform",
                "Sec-Fetch-Dest",
                "Sec-Fetch-Mode",
                "Sec-Fetch-Site",
                HttpHeaders.COOKIE
        );
    }

    private String buildCookieHeader(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) {
            return "";
        }
        List<String> cookies = new ArrayList<>();
        for (String setCookie : setCookies) {
            if (setCookie == null || setCookie.isBlank()) {
                continue;
            }
            String cookie = setCookie.split(";", 2)[0].trim();
            if (!cookie.isBlank()) {
                cookies.add(cookie);
            }
        }
        return String.join("; ", cookies);
    }

    private List<String> cookieNames(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String setCookie : setCookies) {
            if (setCookie == null || setCookie.isBlank()) {
                continue;
            }
            String cookie = setCookie.split(";", 2)[0];
            int separator = cookie.indexOf('=');
            if (separator > 0) {
                names.add(cookie.substring(0, separator).trim());
            }
        }
        return names;
    }

    private Map<String, Object> buildPayload(CompanyProfile company, String year, String period, List<String> itemCodes) {
        return buildPayload(company, List.of(year), period, itemCodes);
    }

    private Map<String, Object> buildPayload(CompanyProfile company, List<String> years, String period, List<String> itemCodes) {
        if (company.getKapCompanyId() == null || company.getKapCompanyId().isBlank()) {
            throw new IllegalArgumentException("KAP member id missing for: " + company.getTickerCode());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "companyType", COMPANY_TYPE);
        putStringListIfPresent(payload, "mkkMemberIdList", company.getKapCompanyId());
        putStringListIfPresent(payload, "mkkMemberTitleList", kapTitle(company.getCompanyName()));
        putIfPresent(payload, "yearList", years);
        putStringListIfPresent(payload, "periodList", period);
        putIfPresent(payload, "itemIdList", itemCodes);
        putIfPresent(payload, "sectors", List.of(SECTOR));
        return payload;
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private void putStringListIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null) {
            payload.put(key, List.of(value));
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return payload.toString();
        }
    }

    private KapFinancialTableResult parseWorkbook(byte[] xlsx, String contentType) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<SheetDebugInfo> sheetInfos = inspectSheets(workbook, evaluator);
            List<String> sheetNames = sheetInfos.stream().map(SheetDebugInfo::sheetName).toList();
            logger.info("KAP XLSX workbook sheets. sheetCount={}, sheetNames={}",
                    workbook.getNumberOfSheets(), sheetNames);
            for (SheetDebugInfo info : sheetInfos) {
                logger.info("KAP XLSX sheet preview. sheetName={}, physicalRowCount={}, first10Rows={}",
                        info.sheetName(), info.physicalRowCount(), info.previewFirst10());
            }
            Sheet sheet = selectSheet(sheetInfos).map(info -> workbook.getSheetAt(info.index())).orElse(null);
            if (sheet == null) {
                return new KapFinancialTableResult(xlsx.length, contentType, null, sheetNames, 0, Map.of(),
                        "Workbook has no sheets", null, List.of(), null, null, null);
            }

            int lastRow = sheet.getLastRowNum();
            int scanLimit = Math.min(Math.max(lastRow + 1, 4), 200);

            List<String> previewRows = new ArrayList<>();
            List<String> allRowTexts = new ArrayList<>();
            int scannedRows = 0;

            for (int i = 0; i < scanLimit; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                scannedRows++;
                if (allRowTexts.size() < 50) {
                    allRowTexts.add("row" + i + ": " + rowText(sheet, row, evaluator));
                }
                if (previewRows.size() < FAILURE_PREVIEW_ROW_LIMIT) {
                    previewRows.add(previewRow(sheet, row, evaluator));
                }
            }

            Optional<ColumnarParseResult> columnarOpt = parseColumnarExport(sheet, evaluator, scanLimit);
            Map<FinancialItemKey, ParsedFinancialItem> items;
            List<String> unmatchedLabels;
            if (columnarOpt.isPresent()) {
                items = columnarOpt.get().items();
                unmatchedLabels = columnarOpt.get().unmatchedLabels();
            } else {
                unmatchedLabels = List.of();
                Map<FinancialItemKey, ParsedFinancialItem> rowItems = new EnumMap<>(FinancialItemKey.class);
                for (int i = 0; i < scanLimit; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    Optional<RowMatch> rowMatch = matchRow(sheet, row, evaluator);
                    if (rowMatch.isEmpty()) continue;
                    RowMatch match = rowMatch.get();
                    if (!rowItems.containsKey(match.itemKey())) {
                        rowItems.put(match.itemKey(), new ParsedFinancialItem(match.itemKey(), match.rawLabel(), match.value(), "TRY", 1));
                    }
                }
                items = rowItems;
            }

            List<String> matchedLabels = new ArrayList<>();
            Integer firstMatchedRow = null;
            for (int i = 0; i < scanLimit; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String rowTxt = rowText(sheet, row, evaluator);
                String normalizedRowTxt = normalizeTurkish(rowTxt);
                for (Map.Entry<FinancialItemKey, List<String>> entry : ITEM_KEY_BY_LABEL.entrySet()) {
                    boolean matched = entry.getValue().stream().map(this::normalizeTurkish).anyMatch(normalizedRowTxt::contains);
                    if (matched) {
                        String name = entry.getKey().name();
                        if (!matchedLabels.contains(name)) matchedLabels.add(name);
                        if (firstMatchedRow == null) firstMatchedRow = i;
                    }
                }
                for (Map.Entry<String, FinancialItemKey> entry : ITEM_KEY_BY_CODE.entrySet()) {
                    if (rowTxt.contains(entry.getKey())) {
                        String name = entry.getValue().name();
                        if (!matchedLabels.contains(name)) matchedLabels.add(name);
                        if (firstMatchedRow == null) firstMatchedRow = i;
                    }
                }
            }

            if (items.isEmpty()) {
                logger.warn("KAP XLSX parse 0 items. sheetName={}, scannedRows={}, scanLimit={}, lastRowNum={}, matchedLabels={}, rowDump={}",
                        sheet.getSheetName(), scannedRows, scanLimit, lastRow, matchedLabels, allRowTexts);
            }

            String rowDumpIfEmpty = items.isEmpty() ? String.join("\n", allRowTexts) : null;

            return new KapFinancialTableResult(
                    xlsx.length,
                    contentType,
                    sheet.getSheetName(),
                    sheetNames,
                    scannedRows,
                    items,
                    firstRows(previewRows, PREVIEW_ROW_LIMIT),
                    String.join("\n", previewRows),
                    unmatchedLabels,
                    matchedLabels.isEmpty() ? null : matchedLabels,
                    firstMatchedRow,
                    rowDumpIfEmpty);
        } catch (Exception e) {
            throw new IllegalStateException("XLSX parse failed: " + e.getMessage(), e);
        }
    }

    private KapFinancialBackfillTableResult parseBackfillWorkbook(byte[] xlsx, String contentType) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<SheetDebugInfo> sheetInfos = inspectSheets(workbook, evaluator);
            List<String> sheetNames = sheetInfos.stream().map(SheetDebugInfo::sheetName).toList();
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return new KapFinancialBackfillTableResult(xlsx.length, contentType, null, sheetNames, 0, List.of());
            }
            List<ParsedFinancialRow> rows = parseColumnarExportRows(sheet, evaluator);
            logger.info("KAP XLSX backfill parse. sheetName={}, scannedRowCount={}, parsedRows={}",
                    sheet.getSheetName(), sheet.getPhysicalNumberOfRows(), rows.size());
            return new KapFinancialBackfillTableResult(
                    xlsx.length,
                    contentType,
                    sheet.getSheetName(),
                    sheetNames,
                    sheet.getPhysicalNumberOfRows(),
                    rows);
        } catch (Exception e) {
            throw new IllegalStateException("XLSX backfill parse failed: " + e.getMessage(), e);
        }
    }

    private List<SheetDebugInfo> inspectSheets(Workbook workbook, FormulaEvaluator evaluator) {
        List<SheetDebugInfo> sheetInfos = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            List<String> first10 = new ArrayList<>();
            List<String> first20 = new ArrayList<>();
            StringBuilder allPreviewText = new StringBuilder();
            for (Row row : sheet) {
                String preview = previewRow(sheet, row, evaluator);
                if (first10.size() < 10) {
                    first10.add(preview);
                }
                if (first20.size() < FAILURE_PREVIEW_ROW_LIMIT) {
                    first20.add(preview);
                }
                allPreviewText.append(' ').append(preview);
            }
            int signalScore = sheetSignalScore(sheet.getSheetName() + " " + allPreviewText);
            sheetInfos.add(new SheetDebugInfo(
                    i,
                    sheet.getSheetName(),
                    sheet.getPhysicalNumberOfRows(),
                    signalScore,
                    String.join("\n", first10),
                    String.join("\n", first20)));
        }
        return sheetInfos;
    }

    private Optional<SheetDebugInfo> selectSheet(List<SheetDebugInfo> sheetInfos) {
        return sheetInfos.stream()
                .max((left, right) -> {
                    int scoreCompare = Integer.compare(left.signalScore(), right.signalScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return Integer.compare(left.physicalRowCount(), right.physicalRowCount());
                });
    }

    private int sheetSignalScore(String text) {
        String normalized = normalizeTurkish(text);
        int score = 0;
        for (String term : SHEET_SIGNAL_TERMS) {
            if (normalized.contains(normalizeTurkish(term))) {
                score++;
            }
        }
        return score;
    }

    private boolean isHtmlDataSheet(String sheetName) {
        return "html data".equalsIgnoreCase(sheetName != null ? sheetName.trim() : "");
    }

    private void debugWorkbookXml(byte[] xlsx) {
        try {
            Path tempFile = Files.createTempFile("kap-compareItems-", ".xlsx");
            Files.write(tempFile, xlsx);
            logger.info("KAP XLSX contains only HTML Data. tempFile={}", tempFile);
            try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(tempFile))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.getName().startsWith("xl/worksheets/") && entry.getName().endsWith(".xml")) {
                        logger.info("KAP XLSX worksheet xml entry. name={}, compressedSize={}, size={}",
                                entry.getName(), entry.getCompressedSize(), entry.getSize());
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("KAP XLSX worksheet XML debug failed. error={}", e.getMessage());
        }
    }

    private String firstRows(List<String> rows, int limit) {
        return String.join("\n", rows.stream().limit(limit).toList());
    }

    private Optional<ColumnarParseResult> parseColumnarExport(Sheet sheet, FormulaEvaluator evaluator, int scanLimit) {
        FinancialHeaderMatch headerMatch = null;
        for (int i = 0; i < scanLimit; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            FinancialHeaderMatch candidate = detectFinancialHeader(sheet, row, evaluator);
            if (candidate.itemColumns().isEmpty()) {
                continue;
            }
            if (headerMatch == null || candidate.itemColumns().size() > headerMatch.itemColumns().size()) {
                headerMatch = candidate;
            }
        }

        if (headerMatch == null) {
            logger.warn("KAP parseColumnarExport no financial header found. sheetName={}, scanLimit={}, physicalRows={}",
                    sheet.getSheetName(), scanLimit, sheet.getPhysicalNumberOfRows());
            return Optional.empty();
        }

        Row headerRow = headerMatch.headerRow();
        Row dataRow = nextDataRow(sheet, headerRow.getRowNum());
        if (dataRow == null) {
            return Optional.of(new ColumnarParseResult(Map.of(), List.of()));
        }

        int unitMultiplier = 1;
        String currency = "TRY";

        if (headerMatch.unitColumn() != null) {
            String unitText = cellText(sheet, dataRow.getCell(headerMatch.unitColumn()), evaluator);
            unitMultiplier = parseUnitMultiplier(unitText);
            currency = parseCurrency(unitText);
        }

        Map<FinancialItemKey, ParsedFinancialItem> items = new EnumMap<>(FinancialItemKey.class);
        Map<String, String> parsedRowValues = new LinkedHashMap<>();
        for (String header : headerMatch.detectedHeaders()) {
            Integer columnIndex = headerMatch.headerColumns().get(header);
            if (columnIndex != null) {
                parsedRowValues.put(header, cellText(sheet, dataRow.getCell(columnIndex), evaluator));
            }
        }

        for (Map.Entry<FinancialItemKey, Integer> entry : headerMatch.itemColumns().entrySet()) {
            Cell valueCell = dataRow.getCell(entry.getValue());
            Optional<BigDecimal> value = parseKapExportValue(sheet, valueCell, evaluator);
            if (value.isPresent()) {
                FinancialItemKey key = entry.getKey();
                items.put(key, new ParsedFinancialItem(key, headerMatch.rawLabels().getOrDefault(key, key.name()), value.get(), currency, unitMultiplier));
            }
        }
        List<String> unmatchedLabels = new ArrayList<>();
        for (Map.Entry<String, Integer> headerEntry : headerMatch.headerColumns().entrySet()) {
            if (!headerMatch.itemColumns().containsValue(headerEntry.getValue())) {
                unmatchedLabels.add(headerEntry.getKey());
            }
        }
        logger.info("KAP XLSX columnar parse. sheetName={}, detectedFinancialHeaderRowIndex={}, dataRow={}, detectedHeaders={}, parsedRowValues={}, matchedItems={}, matchedItemCount={}, currency={}, unitMultiplier={}",
                sheet.getSheetName(),
                headerRow.getRowNum(),
                dataRow.getRowNum(),
                headerMatch.detectedHeaders(),
                parsedRowValues,
                items,
                items.size(),
                currency,
                unitMultiplier);
        return Optional.of(new ColumnarParseResult(items, unmatchedLabels));
    }

    private List<ParsedFinancialRow> parseColumnarExportRows(Sheet sheet, FormulaEvaluator evaluator) {
        FinancialHeaderMatch headerMatch = null;
        for (Row row : sheet) {
            FinancialHeaderMatch candidate = detectFinancialHeader(sheet, row, evaluator);
            if (candidate.itemColumns().isEmpty()) {
                continue;
            }
            if (headerMatch == null || candidate.itemColumns().size() > headerMatch.itemColumns().size()) {
                headerMatch = candidate;
            }
        }
        if (headerMatch == null) {
            return List.of();
        }

        Integer yearColumn = findHeaderColumn(headerMatch, "yil");
        Integer periodColumn = findHeaderColumn(headerMatch, "periyot");
        if (yearColumn == null || periodColumn == null) {
            logger.warn("KAP XLSX backfill missing year/period columns. sheetName={}, detectedHeaders={}",
                    sheet.getSheetName(), headerMatch.detectedHeaders());
            return List.of();
        }

        List<ParsedFinancialRow> rows = new ArrayList<>();
        for (int i = headerMatch.headerRow().getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row dataRow = sheet.getRow(i);
            if (dataRow == null || dataRow.getPhysicalNumberOfCells() == 0) {
                continue;
            }
            Integer year = parseIntegerCell(sheet, dataRow.getCell(yearColumn), evaluator);
            Integer period = parseIntegerCell(sheet, dataRow.getCell(periodColumn), evaluator);
            if (year == null || period == null) {
                continue;
            }

            int unitMultiplier = 1;
            String currency = "TRY";
            if (headerMatch.unitColumn() != null) {
                String unitText = cellText(sheet, dataRow.getCell(headerMatch.unitColumn()), evaluator);
                unitMultiplier = parseUnitMultiplier(unitText);
                currency = parseCurrency(unitText);
            }

            Map<FinancialItemKey, ParsedFinancialItem> items = new EnumMap<>(FinancialItemKey.class);
            for (Map.Entry<FinancialItemKey, Integer> entry : headerMatch.itemColumns().entrySet()) {
                Optional<BigDecimal> value = parseKapExportValue(sheet, dataRow.getCell(entry.getValue()), evaluator);
                if (value.isPresent()) {
                    FinancialItemKey key = entry.getKey();
                    items.put(key, new ParsedFinancialItem(
                            key,
                            headerMatch.rawLabels().getOrDefault(key, key.name()),
                            value.get(),
                            currency,
                            unitMultiplier));
                }
            }
            if (!items.isEmpty()) {
                rows.add(new ParsedFinancialRow(year, period, items));
            }
        }
        return rows;
    }

    private Integer findHeaderColumn(FinancialHeaderMatch headerMatch, String normalizedNeedle) {
        for (Map.Entry<String, Integer> entry : headerMatch.headerColumns().entrySet()) {
            if (normalizeTurkish(entry.getKey()).contains(normalizedNeedle)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Integer parseIntegerCell(Sheet sheet, Cell cell, FormulaEvaluator evaluator) {
        String text = cellText(sheet, cell, evaluator);
        if (text == null || text.isBlank()) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FinancialHeaderMatch detectFinancialHeader(Sheet sheet, Row row, FormulaEvaluator evaluator) {
        Map<FinancialItemKey, Integer> itemColumns = new EnumMap<>(FinancialItemKey.class);
        Map<FinancialItemKey, String> rawLabels = new EnumMap<>(FinancialItemKey.class);
        Map<String, Integer> headerColumns = new LinkedHashMap<>();
        List<String> detectedHeaders = new ArrayList<>();
        Integer unitColumn = null;

        for (Cell cell : row) {
            String header = cellText(sheet, cell, evaluator);
            if (header == null || header.isBlank()) {
                continue;
            }
            String trimmedHeader = header.trim();
            detectedHeaders.add(trimmedHeader);
            headerColumns.put(trimmedHeader, cell.getColumnIndex());

            String normalizedHeader = normalizeTurkish(trimmedHeader);
            if (normalizedHeader.contains("sunum para birimi")) {
                unitColumn = cell.getColumnIndex();
                continue;
            }
            Optional<FinancialItemKey> itemKey = mapHeaderToItemKey(trimmedHeader);
            if (itemKey.isEmpty()) {
                FinancialItemKey fromCode = ITEM_KEY_BY_CODE.get(trimmedHeader);
                if (fromCode != null) itemKey = Optional.of(fromCode);
            }
            if (itemKey.isPresent()) {
                itemColumns.put(itemKey.get(), cell.getColumnIndex());
                rawLabels.put(itemKey.get(), trimmedHeader);
            }
        }

        logger.info("KAP detectFinancialHeader. sheetName={}, rowIdx={}, detectedHeaders={}, matchedItemColumns={}",
                sheet.getSheetName(), row.getRowNum(), detectedHeaders, itemColumns.keySet());
        return new FinancialHeaderMatch(row, detectedHeaders, headerColumns, itemColumns, rawLabels, unitColumn);
    }

    private Row nextDataRow(Sheet sheet, int headerRowIndex) {
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getPhysicalNumberOfCells() > 0) {
                return row;
            }
        }
        return null;
    }

    private Optional<FinancialItemKey> mapHeaderToItemKey(String header) {
        String normalized = normalizeTurkish(header);
        if (normalized.equals("hasilat")
                || normalized.contains("finans sektoru faaliyetleri hasilati")
                || normalized.contains("satis gelirleri")
                || normalized.contains("revenue")) {
            return Optional.of(FinancialItemKey.HASILAT);
        }
        if (normalized.contains("net donem kari") || normalized.contains("net donem kari zarari")) {
            return Optional.of(FinancialItemKey.NET_DONEM_KARI);
        }
        if (normalized.contains("toplam ozkaynaklar") || normalized.equals("ozkaynaklar")) {
            return Optional.of(FinancialItemKey.OZKAYNAKLAR);
        }
        if (normalized.contains("toplam varliklar") || normalized.equals("assets")) {
            return Optional.of(FinancialItemKey.TOPLAM_VARLIKLAR);
        }
        if (normalized.contains("toplam kaynaklar") || normalized.contains("equity and liabilities")) {
            return Optional.of(FinancialItemKey.TOPLAM_KAYNAKLAR);
        }
        if (normalized.contains("brut kar zarar")
                || normalized.equals("brut kar")
                || normalized.contains("brut esas faaliyet kari")
                || normalized.contains("gross profit")) {
            return Optional.of(FinancialItemKey.BRUT_KAR);
        }
        if (normalized.contains("kisa vadeli yukumlulukler")) {
            return Optional.of(FinancialItemKey.KISA_VADELI_YUKUMLULUKLER);
        }
        if (normalized.contains("uzun vadeli yukumlulukler")) {
            return Optional.of(FinancialItemKey.UZUN_VADELI_YUKUMLULUKLER);
        }
        return Optional.empty();
    }

    private int parseUnitMultiplier(String unitText) {
        if (unitText == null) {
            return 1;
        }
        String digits = unitText.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String parseCurrency(String unitText) {
        if (unitText == null) {
            return "TRY";
        }
        String normalized = unitText.toUpperCase(Locale.ROOT);
        if (normalized.contains("TL") || normalized.contains("TRY")) {
            return "TRY";
        }
        return "TRY";
    }

    private Optional<RowMatch> matchRow(Sheet sheet, Row row, FormulaEvaluator evaluator) {
        String visibleRowText = rowText(sheet, row, evaluator);
        String rowText = normalizeTurkish(visibleRowText);
        for (Map.Entry<String, FinancialItemKey> entry : ITEM_KEY_BY_CODE.entrySet()) {
            if (!normalize(rowText).contains(normalize(entry.getKey()))) {
                continue;
            }
            Optional<BigDecimal> value = findCurrentPeriodValue(sheet, row, evaluator);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RowMatch(entry.getValue(), findRawLabel(sheet, row, evaluator, entry.getKey()), value.get()));
        }
        for (Map.Entry<FinancialItemKey, List<String>> entry : ITEM_KEY_BY_LABEL.entrySet()) {
            boolean matched = entry.getValue().stream()
                    .map(this::normalizeTurkish)
                    .anyMatch(rowText::contains);
            if (!matched) {
                continue;
            }
            Optional<BigDecimal> value = findCurrentPeriodValue(sheet, row, evaluator);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RowMatch(entry.getKey(), findRawLabel(sheet, row, evaluator, null), value.get()));
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> findCurrentPeriodValue(Sheet sheet, Row row, FormulaEvaluator evaluator) {
        BigDecimal selected = null;
        for (Cell cell : row) {
            Optional<BigDecimal> value = parseNumericCell(sheet, cell, evaluator);
            if (value.isPresent()) {
                selected = value.get();
            }
        }
        return Optional.ofNullable(selected);
    }

    private Optional<BigDecimal> parseNumericCell(Sheet sheet, Cell cell, FormulaEvaluator evaluator) {
        if (cell == null || cell.getCellType() == CellType.BLANK || cell.getCellType() == CellType.BOOLEAN) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
            try {
                return Optional.of(BigDecimal.valueOf(cell.getCellType() == CellType.FORMULA
                        ? evaluator.evaluate(cell).getNumberValue()
                        : cell.getNumericCellValue()));
            } catch (IllegalStateException ignored) {
                return parseDecimal(cellText(sheet, cell, evaluator));
            }
        }
        return parseDecimal(cellText(sheet, cell, evaluator));
    }

    private Optional<BigDecimal> parseKapExportValue(Sheet sheet, Cell cell, FormulaEvaluator evaluator) {
        if (cell == null || cell.getCellType() == CellType.BLANK || cell.getCellType() == CellType.BOOLEAN) {
            return Optional.empty();
        }
        Optional<BigDecimal> formattedValue = parseKapExportDecimal(cellText(sheet, cell, evaluator));
        if (formattedValue.isPresent()) {
            return formattedValue;
        }
        if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
            try {
                return Optional.of(BigDecimal.valueOf(cell.getCellType() == CellType.FORMULA
                        ? evaluator.evaluate(cell).getNumberValue()
                        : cell.getNumericCellValue()));
            } catch (IllegalStateException ignored) {
                return parseKapExportDecimal(cellText(sheet, cell, evaluator));
            }
        }
        return parseKapExportDecimal(cellText(sheet, cell, evaluator));
    }

    private Optional<BigDecimal> parseKapExportDecimal(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String s = raw.trim();
        if ("-".equals(s) || "--".equals(s) || "null".equalsIgnoreCase(s)) return Optional.empty();

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

    private Optional<BigDecimal> parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String s = raw.trim();
        if ("-".equals(s) || "--".equals(s) || "null".equalsIgnoreCase(s)) return Optional.empty();

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

    private String rowText(Sheet sheet, Row row, FormulaEvaluator evaluator) {
        List<String> values = new ArrayList<>();
        for (Cell cell : row) {
            String value = cellText(sheet, cell, evaluator);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return String.join(" ", values);
    }

    private String findRawLabel(Sheet sheet, Row row, FormulaEvaluator evaluator, String itemCode) {
        for (Cell cell : row) {
            String value = cellText(sheet, cell, evaluator);
            if (value != null && !value.isBlank() && (itemCode == null || !normalize(value).contains(normalize(itemCode)))) {
                return value.trim();
            }
        }
        return itemCode != null ? itemCode : rowText(sheet, row, evaluator);
    }

    private String previewRow(Sheet sheet, Row row, FormulaEvaluator evaluator) {
        List<String> values = new ArrayList<>();
        int count = 0;
        for (Cell cell : row) {
            if (count >= PREVIEW_CELL_LIMIT) {
                break;
            }
            String value = cellText(sheet, cell, evaluator);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
                count++;
            }
        }
        return String.join(" | ", values);
    }

    private String cellText(Sheet sheet, Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        Cell mergedCell = resolveMergedCell(sheet, cell);
        try {
            return dataFormatter.formatCellValue(mergedCell, evaluator);
        } catch (Exception e) {
            return dataFormatter.formatCellValue(mergedCell);
        }
    }

    private Cell resolveMergedCell(Sheet sheet, Cell cell) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(cell.getRowIndex(), cell.getColumnIndex())) {
                Row firstRow = sheet.getRow(range.getFirstRow());
                if (firstRow == null) {
                    return cell;
                }
                Cell firstCell = firstRow.getCell(range.getFirstColumn());
                return firstCell != null ? firstCell : cell;
            }
        }
        return cell;
    }

    private String mapPeriod(Integer quarter) {
        if (quarter == null) {
            return "4";
        }
        if (quarter <= 1) return "1";
        if (quarter == 2) return "2";
        if (quarter == 3) return "3";
        return "4";
    }

    private String kapTitle(String companyName) {
        return companyName == null ? null : companyName.toUpperCase(Locale.forLanguageTag("tr-TR"));
    }

    // KAP occasionally returns 500 with a body that contains "HTTP 400" when an item is not supported
    // (e.g. ifrs-full_GrossProfit for defence companies). Treat this as a retryable 4xx.
    private boolean isKapWrapped400(RestClientResponseException e) {
        if (!e.getStatusCode().is5xxServerError()) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        return body != null && body.contains("HTTP 400");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", " ").trim();
    }

    private String normalizeTurkish(String value) {
        if (value == null) {
            return "";
        }
        return value
                .toLowerCase(Locale.forLanguageTag("tr-TR"))
                .replace('ı', 'i')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ş', 's')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replace('â', 'a')
                .replaceAll("[()]", " ")
                .replaceAll("[^a-z0-9_ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record KapFinancialTableResult(
            int xlsxByteSize,
            String contentType,
            String sheetName,
            List<String> workbookSheetNames,
            int scannedRowCount,
            Map<FinancialItemKey, ParsedFinancialItem> items,
            String preview,
            String rowPreviewFirst20,
            List<String> unmatchedLabels,
            List<String> matchedLabels,
            Integer firstMatchedRow,
            String rowDumpIfEmpty
    ) {
    }

    public record ParsedFinancialItem(FinancialItemKey itemKey, String rawLabel, BigDecimal value, String currency, int unitMultiplier) {
    }

    public record ParsedFinancialRow(Integer year, Integer period, Map<FinancialItemKey, ParsedFinancialItem> items) {
    }

    public record KapFinancialBackfillTableResult(
            int xlsxByteSize,
            String contentType,
            String sheetName,
            List<String> workbookSheetNames,
            int scannedRowCount,
            List<ParsedFinancialRow> rows
    ) {
    }

    private record ColumnarParseResult(
            Map<FinancialItemKey, ParsedFinancialItem> items,
            List<String> unmatchedLabels
    ) {
    }

    private record RowMatch(FinancialItemKey itemKey, String rawLabel, BigDecimal value) {
    }

    private record SheetDebugInfo(
            int index,
            String sheetName,
            int physicalRowCount,
            int signalScore,
            String previewFirst10,
            String previewFirst20
    ) {
    }

    private record FinancialHeaderMatch(
            Row headerRow,
            List<String> detectedHeaders,
            Map<String, Integer> headerColumns,
            Map<FinancialItemKey, Integer> itemColumns,
            Map<FinancialItemKey, String> rawLabels,
            Integer unitColumn
    ) {
    }
}
