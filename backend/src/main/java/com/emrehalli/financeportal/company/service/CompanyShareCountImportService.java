package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.dto.response.ShareCountImportResponse;
import com.emrehalli.financeportal.company.dto.response.ShareCountImportRowError;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanyShareCountImportService {

    private static final List<String> TICKER_HEADERS = List.of("ticker_code", "ticker", "symbol");
    private static final List<String> SHARE_COUNT_HEADERS = List.of(
            "shares_outstanding",
            "share_count",
            "paid_in_capital",
            "paid_capital",
            "cikarilmis_sermaye",
            "odenmis_sermaye",
            "çıkarılmış_sermaye",
            "ödenmiş_sermaye",
            "çıkarılmış sermaye",
            "ödenmiş sermaye"
    );

    private final CompanyProfileRepository companyProfileRepository;
    private final CompanyRatioService companyRatioService;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

    public CompanyShareCountImportService(CompanyProfileRepository companyProfileRepository,
                                          CompanyRatioService companyRatioService) {
        this.companyProfileRepository = companyProfileRepository;
        this.companyRatioService = companyRatioService;
    }

    @Transactional
    public ShareCountImportResponse importShareCounts(MultipartFile file, boolean overwrite) {
        ParseResult parseResult = parse(file);

        Set<String> updatedShareCounts = new LinkedHashSet<>();
        Set<String> skippedExisting = new LinkedHashSet<>();
        Set<String> notFoundTickers = new LinkedHashSet<>();
        List<ShareCountImportRowError> invalidRows = new ArrayList<>(parseResult.invalidRows());
        Set<String> tickersToRecalculate = new LinkedHashSet<>();

        for (ShareCountRow row : parseResult.rows()) {
            String ticker = normalizeTicker(row.ticker());
            if (ticker == null) {
                invalidRows.add(error(row.rowNumber(), null, "ticker_code bos veya gecersiz."));
                continue;
            }

            Optional<CompanyProfile> optionalCompany = companyProfileRepository.findByTickerCodeIgnoreCase(ticker);
            if (optionalCompany.isEmpty()) {
                notFoundTickers.add(ticker);
                continue;
            }

            BigDecimal shareCount = parseShareCount(row.shareCount());
            if (shareCount == null) {
                invalidRows.add(error(row.rowNumber(), ticker, "shares_outstanding parse edilemedi."));
                continue;
            }
            if (shareCount.compareTo(BigDecimal.ZERO) <= 0) {
                invalidRows.add(error(row.rowNumber(), ticker, "shares_outstanding pozitif olmali."));
                continue;
            }

            CompanyProfile company = optionalCompany.get();
            BigDecimal existing = company.getSharesOutstanding();
            if (existing != null && existing.compareTo(BigDecimal.ZERO) > 0 && !overwrite) {
                skippedExisting.add(ticker);
                continue;
            }
            if (existing != null && existing.compareTo(shareCount) == 0) {
                skippedExisting.add(ticker);
                tickersToRecalculate.add(ticker);
                continue;
            }

            company.setSharesOutstanding(shareCount);
            company.setUpdatedAt(OffsetDateTime.now());
            companyProfileRepository.save(company);
            updatedShareCounts.add(ticker);
            tickersToRecalculate.add(ticker);
        }

        List<String> recalculatedTickers = new ArrayList<>();
        for (String ticker : tickersToRecalculate) {
            try {
                var result = companyRatioService.calculateForTicker(ticker);
                if (result.isCalculated()) {
                    recalculatedTickers.add(ticker);
                } else {
                    invalidRows.add(error(null, ticker, result.getFailedReason()));
                }
            } catch (Exception e) {
                invalidRows.add(error(null, ticker, "Oran hesaplama hatasi: " + e.getMessage()));
            }
        }

        return ShareCountImportResponse.builder()
                .updatedShareCounts(new ArrayList<>(updatedShareCounts))
                .skippedExisting(new ArrayList<>(skippedExisting))
                .notFoundTickers(new ArrayList<>(notFoundTickers))
                .invalidRows(invalidRows)
                .recalculatedTickers(recalculatedTickers)
                .build();
    }

    private ParseResult parse(MultipartFile file) {
        List<ShareCountRow> rows = new ArrayList<>();
        List<ShareCountImportRowError> invalidRows = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            invalidRows.add(error(null, null, "Dosya bos."));
            return new ParseResult(rows, invalidRows);
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        try {
            if (filename.endsWith(".xlsx")) {
                parseXlsx(file, rows, invalidRows);
            } else {
                parseCsv(file, rows, invalidRows);
            }
        } catch (Exception e) {
            invalidRows.add(error(null, null, "Dosya parse edilemedi: " + e.getMessage()));
        }

        return new ParseResult(rows, invalidRows);
    }

    private void parseCsv(MultipartFile file,
                          List<ShareCountRow> rows,
                          List<ShareCountImportRowError> invalidRows) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                invalidRows.add(error(1, null, "CSV bos."));
                return;
            }

            List<String> headerValues = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> headerIndex = buildHeaderIndex(headerValues);
            if (!hasAny(headerIndex, TICKER_HEADERS) || !hasAny(headerIndex, SHARE_COUNT_HEADERS)) {
                invalidRows.add(error(1, null, "ticker_code ve shares_outstanding kolonu gerekli."));
                return;
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                rows.add(new ShareCountRow(
                        lineNumber,
                        valueOfAny(headerIndex, values, TICKER_HEADERS),
                        valueOfAny(headerIndex, values, SHARE_COUNT_HEADERS)
                ));
            }
        }
    }

    private void parseXlsx(MultipartFile file,
                           List<ShareCountRow> rows,
                           List<ShareCountImportRowError> invalidRows) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                invalidRows.add(error(1, null, "XLSX bos."));
                return;
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                invalidRows.add(error(1, null, "XLSX header bulunamadi."));
                return;
            }

            Map<String, Integer> headerIndex = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                headerIndex.put(normalizeHeader(dataFormatter.formatCellValue(cell)), cell.getColumnIndex());
            }
            if (!hasAny(headerIndex, TICKER_HEADERS) || !hasAny(headerIndex, SHARE_COUNT_HEADERS)) {
                invalidRows.add(error(1, null, "ticker_code ve shares_outstanding kolonu gerekli."));
                return;
            }

            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String ticker = cellValue(row, headerIndex, TICKER_HEADERS);
                String shareCount = cellValue(row, headerIndex, SHARE_COUNT_HEADERS);
                if ((ticker == null || ticker.isBlank()) && (shareCount == null || shareCount.isBlank())) {
                    continue;
                }
                rows.add(new ShareCountRow(rowIndex + 1, ticker, shareCount));
            }
        }
    }

    private String cellValue(Row row, Map<String, Integer> headerIndex, List<String> aliases) {
        for (String alias : aliases) {
            Integer cellIndex = headerIndex.get(alias);
            if (cellIndex != null) {
                Cell cell = row.getCell(cellIndex);
                if (cell != null) {
                    return dataFormatter.formatCellValue(cell).trim();
                }
            }
        }
        return "";
    }

    private boolean hasAny(Map<String, Integer> headerIndex, List<String> aliases) {
        return aliases.stream().anyMatch(headerIndex::containsKey);
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerIndex.put(normalizeHeader(headers.get(i)), i);
        }
        return headerIndex;
    }

    private String valueOfAny(Map<String, Integer> headerIndex, List<String> values, List<String> aliases) {
        for (String alias : aliases) {
            Integer index = headerIndex.get(alias);
            if (index != null && index < values.size()) {
                return values.get(index).trim();
            }
        }
        return "";
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        values.add(current.toString().trim());
        return values;
    }

    private String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF'
                ? value.substring(1)
                : value;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('\u0131', 'i')
                .replace('\u0130', 'i')
                .replace('\u015f', 's')
                .replace('\u015e', 's')
                .replace('\u011f', 'g')
                .replace('\u011e', 'g')
                .replace('\u00fc', 'u')
                .replace('\u00dc', 'u')
                .replace('\u00f6', 'o')
                .replace('\u00d6', 'o')
                .replace('\u00e7', 'c')
                .replace('\u00c7', 'c');
    }

    private String normalizeTicker(String ticker) {
        return ticker == null || ticker.isBlank() ? null : ticker.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal parseShareCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replace(" ", "");
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ShareCountImportRowError error(Integer rowNumber, String ticker, String message) {
        return ShareCountImportRowError.builder()
                .rowNumber(rowNumber)
                .ticker(ticker)
                .message(message)
                .build();
    }

    private record ShareCountRow(int rowNumber, String ticker, String shareCount) {
    }

    private record ParseResult(List<ShareCountRow> rows, List<ShareCountImportRowError> invalidRows) {
    }
}
