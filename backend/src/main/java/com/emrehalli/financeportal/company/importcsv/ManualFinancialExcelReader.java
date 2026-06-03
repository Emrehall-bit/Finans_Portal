package com.emrehalli.financeportal.company.importcsv;

import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ManualFinancialExcelReader {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "ticker_code", "period_year", "report_type", "published_at",
            "source_url", "item_key", "raw_label", "value", "currency", "unit_multiplier"
    );

    private static final List<String> SHARE_COUNT_HEADERS = List.of(
            "shares_outstanding", "share_count", "odenmis_sermaye",
            "odenmis sermaye", "cikarilmis_sermaye", "cikarilmis sermaye"
    );

    public ManualFinancialCsvReader.CsvReadResult read(MultipartFile file) {
        List<ManualFinancialImportRow> rows = new ArrayList<>();
        List<ManualFinancialImportError> errors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            errors.add(error("XLSX dosyası boş."));
            return new ManualFinancialCsvReader.CsvReadResult(rows, errors);
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                errors.add(error("XLSX dosyası boş."));
                return new ManualFinancialCsvReader.CsvReadResult(rows, errors);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(error("Başlık satırı bulunamadı."));
                return new ManualFinancialCsvReader.CsvReadResult(rows, errors);
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(headerRow);
            validateHeaders(headerIndex.keySet(), errors);
            if (!errors.isEmpty()) {
                return new ManualFinancialCsvReader.CsvReadResult(rows, errors);
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String tickerCell = cellValue(row, headerIndex, "ticker_code");
                if (tickerCell == null || tickerCell.isBlank()) continue;

                rows.add(new ManualFinancialImportRow(
                        i + 1,
                        tickerCell,
                        null,
                        cellValue(row, headerIndex, "period_year"),
                        cellValue(row, headerIndex, "report_type"),
                        cellValue(row, headerIndex, "published_at"),
                        cellValue(row, headerIndex, "source_url"),
                        cellValue(row, headerIndex, "item_key"),
                        cellValue(row, headerIndex, "raw_label"),
                        cellValue(row, headerIndex, "value"),
                        cellValue(row, headerIndex, "currency"),
                        cellValue(row, headerIndex, "unit_multiplier"),
                        cellValueAny(row, headerIndex, SHARE_COUNT_HEADERS)
                ));
            }
        } catch (Exception e) {
            errors.add(error("XLSX okunamadı: " + e.getMessage()));
        }

        return new ManualFinancialCsvReader.CsvReadResult(rows, errors);
    }

    private Map<String, Integer> buildHeaderIndex(Row headerRow) {
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i <= headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String header = normalizeHeader(getCellStringValue(cell));
            if (!header.isBlank()) {
                headerIndex.putIfAbsent(header, i);
            }
        }
        return headerIndex;
    }

    private void validateHeaders(Set<String> availableHeaders, List<ManualFinancialImportError> errors) {
        for (String required : REQUIRED_HEADERS) {
            if (!availableHeaders.contains(required)) {
                errors.add(ManualFinancialImportError.builder()
                        .lineNumber(1).fieldName(required).message("Zorunlu kolon eksik.").build());
            }
        }
    }

    private String cellValue(Row row, Map<String, Integer> headerIndex, String header) {
        Integer idx = headerIndex.get(header);
        if (idx == null) return "";
        Cell cell = row.getCell(idx);
        return cell == null ? "" : getCellStringValue(cell);
    }

    private String cellValueAny(Row row, Map<String, Integer> headerIndex, List<String> headers) {
        for (String header : headers) {
            Integer idx = headerIndex.get(header);
            if (idx == null) continue;
            Cell cell = row.getCell(idx);
            if (cell != null) {
                String val = getCellStringValue(cell);
                if (!val.isBlank()) return val;
            }
        }
        return "";
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate ld = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield ld.toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    yield String.valueOf((long) d);
                }
                yield BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String normalizeHeader(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase()
                .replace('ı', 'i').replace('İ', 'i')
                .replace('ş', 's').replace('Ş', 's')
                .replace('ğ', 'g').replace('Ğ', 'g')
                .replace('ü', 'u').replace('Ü', 'u')
                .replace('ö', 'o').replace('Ö', 'o')
                .replace('ç', 'c').replace('Ç', 'c');
    }

    private ManualFinancialImportError error(String message) {
        return ManualFinancialImportError.builder().fieldName("file").message(message).build();
    }
}
