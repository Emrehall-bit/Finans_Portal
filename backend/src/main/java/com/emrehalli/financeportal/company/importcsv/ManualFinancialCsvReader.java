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

    public CsvReadResult read(MultipartFile file) {
        List<CsvRow> rows = new ArrayList<>();
        List<ManualFinancialImportError> errors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            errors.add(ManualFinancialImportError.builder()
                    .fieldName("file")
                    .message("CSV dosyası boş.")
                    .build());
            return new CsvReadResult(rows, errors);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add(ManualFinancialImportError.builder()
                        .fieldName("file")
                        .message("CSV dosyası boş.")
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
                rows.add(new CsvRow(lineNumber, headerIndex, columns));
            }
        } catch (IOException e) {
            errors.add(ManualFinancialImportError.builder()
                    .fieldName("file")
                    .message("CSV okunamadı: " + e.getMessage())
                    .build());
        }

        return new CsvReadResult(rows, errors);
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
        return value == null ? "" : value.trim().toLowerCase();
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

    public record CsvReadResult(List<CsvRow> rows, List<ManualFinancialImportError> errors) {
    }

    public static final class CsvRow {
        private final int lineNumber;
        private final Map<String, Integer> headerIndex;
        private final List<String> columns;

        private CsvRow(int lineNumber, Map<String, Integer> headerIndex, List<String> columns) {
            this.lineNumber = lineNumber;
            this.headerIndex = headerIndex;
            this.columns = columns;
        }

        public int lineNumber() {
            return lineNumber;
        }

        public String get(String header) {
            Integer index = headerIndex.get(header);
            if (index == null || index >= columns.size()) {
                return "";
            }
            return columns.get(index).trim();
        }
    }
}



