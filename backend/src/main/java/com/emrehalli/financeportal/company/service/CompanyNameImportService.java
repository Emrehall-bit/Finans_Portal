package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.dto.response.CompanyNameImportResponse;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

/**
 * CSV / XLSX dosyasından ticker → company_name eşlemesini okur, zayıf profilleri günceller.
 *
 * Desteklenen formatlar:
 *   1. Özel 2-kolon: ticker_code (veya ticker/symbol/hisse_kodu),  company_name (veya unvan/sirket vb.)
 *   2. KAP "Finansal Tablo Kalem Sorgulama" XLSX: "Şirket" + "Hisse Kodu" kolonları.
 *      Başlık satırı ilk 15 satır içinde aranır (KAP dosyaları 7. satırda başlık içerebilir).
 *
 * Güvenlik: sadece company_name = ticker_code olan (zayıf) profiller güncellenir.
 */
@Service
public class CompanyNameImportService {

    private static final Logger log = LogManager.getLogger(CompanyNameImportService.class);

    // Ticker kolonları — normHeaderTr sonrası karşılaştırma
    // Ticker kolonları — normHeaderTr sonrası karşılaştırma
    // "kod" → BI "KOD" kolonu; "hissekodu" → KAP "Hisse Kodu" kolonu
    private static final List<String> TICKER_NORM = List.of(
            "tickercode", "ticker", "symbol", "hissekodu", "tickerkodu", "sembol", "kod", "hissesembolu");

    // Company name kolonları — normHeaderTr sonrası karşılaştırma
    // "sirket" → KAP "Şirket"; "sirketadi" → BI "Şirket Adı"
    private static final List<String> NAME_NORM = List.of(
            "companyname", "unvan", "sirketadi", "sirketunvani", "uyesirketadi",
            "name", "tamunvan", "fullname", "sirket", "ad", "title");

    private final CompanyProfileRepository profileRepository;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

    public CompanyNameImportService(CompanyProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public CompanyNameImportResponse importNames(MultipartFile file) {
        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";

        List<RawRow> rawRows;
        List<String> errors = new ArrayList<>();

        try {
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                rawRows = parseXlsx(file, errors);
            } else {
                rawRows = parseCsv(file, errors);
            }
        } catch (Exception e) {
            log.error("companyNameImport.parseFailed file={}", filename, e);
            errors.add("Dosya okunamadı: " + e.getMessage());
            return empty(errors);
        }

        if (!errors.isEmpty()) {
            return empty(errors);
        }

        // Aynı ticker için birden fazla satır gelebilir (KAP dosyasında çok dönem).
        // Her ticker için ilk geçerli ismi al.
        Map<String, RawRow> dedupedByTicker = new LinkedHashMap<>();
        for (RawRow row : rawRows) {
            String t = normalizeTicker(row.ticker());
            if (t != null && row.name() != null && !row.name().isBlank()) {
                dedupedByTicker.putIfAbsent(t, row);
            }
        }

        int checked = 0;
        int updated = 0;
        int skippedAlreadyGood = 0;
        Set<String> missingProfiles = new LinkedHashSet<>();
        Set<String> updatedTickers  = new LinkedHashSet<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (RawRow row : dedupedByTicker.values()) {
            String ticker  = normalizeTicker(row.ticker());
            String newName = row.name().trim();

            checked++;

            Optional<CompanyProfile> opt = profileRepository.findByTickerCodeIgnoreCase(ticker);
            if (opt.isEmpty()) {
                missingProfiles.add(ticker);
                log.warn("companyNameImport.notFound ticker={}", ticker);
                continue;
            }

            CompanyProfile profile = opt.get();
            boolean isWeak = profile.getCompanyName() != null
                    && profile.getCompanyName().trim().equalsIgnoreCase(profile.getTickerCode().trim());

            if (!isWeak) {
                skippedAlreadyGood++;
                continue;
            }

            profile.setCompanyName(newName);
            profile.setUpdatedAt(now);
            profileRepository.save(profile);
            updatedTickers.add(ticker);
            updated++;
            log.info("companyNameImport.updated ticker={} newName='{}'", ticker, newName);
        }

        log.info("companyNameImport.done file={} uniqueTickers={} updated={} skipped={} missing={} errors={}",
                filename, dedupedByTicker.size(), updated, skippedAlreadyGood, missingProfiles.size(), errors.size());

        return CompanyNameImportResponse.builder()
                .checked(checked)
                .updated(updated)
                .skippedAlreadyGood(skippedAlreadyGood)
                .missingProfiles(new ArrayList<>(missingProfiles))
                .updatedTickers(new ArrayList<>(updatedTickers))
                .errors(errors)
                .build();
    }

    // -------------------------------------------------------------------------
    // CSV parser
    // -------------------------------------------------------------------------

    private List<RawRow> parseCsv(MultipartFile file, List<String> errors) throws Exception {
        List<RawRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add("Dosya boş.");
                return rows;
            }

            String[] headers = splitCsvLine(headerLine);
            int tickerCol = findCol(headers, TICKER_NORM);
            int nameCol   = findCol(headers, NAME_NORM);

            if (tickerCol < 0 || nameCol < 0) {
                errors.add("Başlıklar bulunamadı. Beklenen: ticker_code, company_name. "
                        + "Dosyadaki başlıklar: [" + String.join(", ", headers) + "]");
                return rows;
            }

            String line;
            int lineNum = 2;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) { lineNum++; continue; }
                String[] parts = splitCsvLine(line);
                rows.add(new RawRow(lineNum, csvCol(parts, tickerCol), csvCol(parts, nameCol)));
                lineNum++;
            }
        }
        return rows;
    }

    private String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    private String csvCol(String[] parts, int idx) {
        if (idx < 0 || idx >= parts.length) return null;
        String s = parts[idx].trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.isBlank() ? null : s;
    }

    // -------------------------------------------------------------------------
    // XLSX parser — ilk 15 satırda başlık arar, KAP formatını da tanır
    // -------------------------------------------------------------------------

    private List<RawRow> parseXlsx(MultipartFile file, List<String> errors) throws Exception {
        List<RawRow> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 0) {
                errors.add("XLSX ilk sayfası boş.");
                return rows;
            }

            int headerRowIdx = -1;
            int tickerCol    = -1;
            int nameCol      = -1;
            List<String> firstRowHeaders = new ArrayList<>();

            int scanLimit = Math.min(14, sheet.getLastRowNum());
            for (int ri = 0; ri <= scanLimit; ri++) {
                Row candidate = sheet.getRow(ri);
                if (candidate == null) continue;

                int tc = -1;
                int nc = -1;
                List<String> rowHeaders = new ArrayList<>();

                for (int c = 0; c <= candidate.getLastCellNum(); c++) {
                    String raw = cellValue(candidate, c);
                    if (raw.isBlank()) continue;
                    rowHeaders.add(raw);
                    String h = normHeaderTr(raw);
                    if (tc < 0 && TICKER_NORM.contains(h)) tc = c;
                    if (nc < 0 && NAME_NORM.contains(h))   nc = c;
                }

                if (ri == 0) firstRowHeaders = rowHeaders;

                if (tc >= 0 && nc >= 0) {
                    headerRowIdx = ri;
                    tickerCol = tc;
                    nameCol   = nc;
                    log.info("companyNameImport.headerFound row={} tickerCol={} nameCol={}",
                            ri, tickerCol, nameCol);
                    break;
                }
            }

            if (headerRowIdx < 0) {
                errors.add("Başlıklar bulunamadı (ilk 15 satır tarandı). "
                        + "Beklenen kolon isimleri: ticker_code/hisse kodu  +  company_name/şirket. "
                        + "Dosyanın ilk satırı: [" + String.join(", ", firstRowHeaders) + "]");
                return rows;
            }

            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String ticker = cellValue(row, tickerCol).trim();
                String name   = cellValue(row, nameCol).trim();
                if (ticker.isBlank() && name.isBlank()) continue;
                rows.add(new RawRow(r + 1,
                        ticker.isBlank() ? null : ticker,
                        name.isBlank()   ? null : name));
            }

            log.info("companyNameImport.xlsxParsed headerRow={} dataRows={}", headerRowIdx, rows.size());
        }
        return rows;
    }

    private String cellValue(Row row, int col) {
        if (col < 0 || row == null) return "";
        var cell = row.getCell(col);
        return cell == null ? "" : dataFormatter.formatCellValue(cell).trim();
    }

    // -------------------------------------------------------------------------
    // Header normalization
    // -------------------------------------------------------------------------

    private int findCol(String[] headers, List<String> normalizedCandidates) {
        for (int i = 0; i < headers.length; i++) {
            if (normalizedCandidates.contains(normHeaderTr(headers[i]))) return i;
        }
        return -1;
    }

    private String normHeaderTr(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .replace('İ', 'I').replace('ı', 'i')
                .replace('Â', 'A').replace('â', 'a')
                .toLowerCase(Locale.ROOT)
                .replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
                .replaceAll("[^a-z0-9]", "");
    }

    // -------------------------------------------------------------------------
    // Misc helpers
    // -------------------------------------------------------------------------

    private String normalizeTicker(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private CompanyNameImportResponse empty(List<String> errors) {
        return CompanyNameImportResponse.builder()
                .checked(0).updated(0).skippedAlreadyGood(0)
                .missingProfiles(List.of()).updatedTickers(List.of()).errors(errors)
                .build();
    }

    private record RawRow(int lineNum, String ticker, String name) {}
}
