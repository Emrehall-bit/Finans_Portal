package com.emrehalli.financeportal.company.importcsv;

import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportError;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ManualFinancialCsvReader {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "ticker_code",
            "period_year",
            "report_type",
            "published_at",
            "source_url",
            "item_key",
            "raw_label",
            "value",
            "currency",
            "unit_multiplier"
    );

    private static final List<String> SHARE_COUNT_HEADERS = List.of(
            "shares_outstanding",
            "share_count",
            "odenmis_sermaye",
            "odenmis sermaye",
            "cikarilmis_sermaye",
            "cikarilmis sermaye",
            "Ã§Ä±karÄ±lmÄ±ÅŸ sermaye",
            "Ã¶denmiÅŸ sermaye"
    );

    public CsvReadResult read(MultipartFile file) {
        List<ManualFinancialImportRow> rows = new ArrayList<>();
        List<ManualFinancialImportError> errors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            errors.add(ManualFinancialImportError.builder()
                    .fieldName("file")
                    .message("CSV dosyasÄ± boÅŸ.")
                    .build());
            return new CsvReadResult(rows, errors);
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (originalFilename.endsWith(".xlsx")) {
            errors.add(ManualFinancialImportError.builder()
                    .fieldName("file")
                    .message("Bu alan manual financial CSV icindir. XLSX desteklenmiyor.")
                    .build());
            return new CsvReadResult(rows, errors);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add(ManualFinancialImportError.builder()
                        .fieldName("file")
                        .message("CSV dosyasÄ± boÅŸ.")
                        .build());
                return new CsvReadResult(rows, errors);
            }

            List<String> headerValues = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> headerIndex = buildHeaderIndex(headerValues);
            validateHeaders(headerIndex.keySet(), errors);
            if (!errors.isEmpty()) {
                return new CsvReadResult(rows, errors);
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> columns = parseCsvLine(line);
                rows.add(new ManualFinancialImportRow(
                        lineNumber,
                        valueOf(headerIndex, columns, "ticker_code"),
                        null,
                        valueOf(headerIndex, columns, "period_year"),
                        valueOf(headerIndex, columns, "report_type"),
                        valueOf(headerIndex, columns, "published_at"),
                        valueOf(headerIndex, columns, "source_url"),
                        valueOf(headerIndex, columns, "item_key"),
                        valueOf(headerIndex, columns, "raw_label"),
                        valueOf(headerIndex, columns, "value"),
                        valueOf(headerIndex, columns, "currency"),
                        valueOf(headerIndex, columns, "unit_multiplier"),
                        valueOfAny(headerIndex, columns, SHARE_COUNT_HEADERS)));
            }
        } catch (IOException e) {
            errors.add(ManualFinancialImportError.builder()
                    .fieldName("file")
                    .message("CSV okunamadÄ±: " + e.getMessage())
                    .build());
        }

        return new CsvReadResult(rows, errors);
    }

    private String valueOf(Map<String, Integer> headerIndex, List<String> columns, String header) {
        Integer index = headerIndex.get(header);
        if (index == null || index >= columns.size()) {
            return "";
        }
        return columns.get(index).trim();
    }

    private String valueOfAny(Map<String, Integer> headerIndex, List<String> columns, List<String> headers) {
        for (String header : headers) {
            Integer index = headerIndex.get(header);
            if (index != null && index < columns.size()) {
                return columns.get(index).trim();
            }
        }
        return "";
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headerValues) {
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < headerValues.size(); i++) {
            headerIndex.put(normalizeHeader(headerValues.get(i)), i);
        }
        return headerIndex;
    }

    private void validateHeaders(Set<String> availableHeaders, List<ManualFinancialImportError> errors) {
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!availableHeaders.contains(requiredHeader)) {
                errors.add(ManualFinancialImportError.builder()
                        .lineNumber(1)
                        .fieldName(requiredHeader)
                        .message("Zorunlu kolon eksik.")
                        .build());
            }
        }
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase()
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

    private String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF'
                ? value.substring(1)
                : value;
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

    public record CsvReadResult(List<ManualFinancialImportRow> rows, List<ManualFinancialImportError> errors) {
    }
}




