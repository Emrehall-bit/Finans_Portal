package com.emrehalli.financeportal.company.importcsv;

import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.enums.FinancialItemKey;
import com.emrehalli.financeportal.company.domain.enums.ReportType;
import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportError;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * KAP "Finansal Tablo Kalem Sorgulama" GENİŞ FORMAT parser.
 *
 * Beklenen başlık kolonları (7. satır civarı, sıra önemli değil):
 *   Şirket | Bildirim ID | Bildirim Yayın Tarihi | Yıl | Periyot |
 *   Finansal Tablo Niteliği | Sunum Para Birimi | Sektörel Tablo Türü |
 *   Toplam Kaynaklar | Kısa Vadeli Yükümlülükler | Uzun Vadeli Yükümlülükler |
 *   Toplam Özkaynaklar | Toplam Varlıklar | Hasılat | Net Dönem Kârı (Zararı)
 *
 * Her satır = bir şirket + bir dönem. Kalem değerleri kolon olarak gelir.
 *
 * Konsolide tercih: aynı (şirket, yıl, periyot) için "Konsolide" varsa o seçilir.
 * unit_multiplier: "Sunum Para Birimi" kolonundan parse edilir ("1.000 TL" → 1000).
 * Şirket eşleşmesi: company_profiles.company_name üzerinden normalize + fuzzy match.
 */
@Component
public class KapFinancialExcelParser {

    private static final Logger log = LogManager.getLogger(KapFinancialExcelParser.class);

    private final CompanyProfileRepository profileRepository;
    private final MarketInstrumentRepository instrumentRepository;

    // Wide format zorunlu tespit kolonları (normalize edilmiş)
    private static final Set<String> WIDE_REQUIRED = Set.of("sirket", "yil", "periyot");

    // Opsiyonel ticker kolonu isimleri — bazı KAP export'larında bulunabilir
    private static final List<String> TICKER_COLS = List.of("hisse kodu", "ticker kodu", "ticker", "sembol");

    // Kalem kolon isimleri (normalize) → FinancialItemKey
    private static final Map<String, FinancialItemKey> ITEM_COL_MAP = new LinkedHashMap<>();
    static {
        ITEM_COL_MAP.put("toplam kaynaklar",                   FinancialItemKey.TOPLAM_KAYNAKLAR);
        ITEM_COL_MAP.put("kisa vadeli yukumlulukler",          FinancialItemKey.KISA_VADELI_YUKUMLULUKLER);
        ITEM_COL_MAP.put("toplam kisa vadeli yukumlulukler",   FinancialItemKey.KISA_VADELI_YUKUMLULUKLER);
        ITEM_COL_MAP.put("uzun vadeli yukumlulukler",          FinancialItemKey.UZUN_VADELI_YUKUMLULUKLER);
        ITEM_COL_MAP.put("toplam uzun vadeli yukumlulukler",   FinancialItemKey.UZUN_VADELI_YUKUMLULUKLER);
        ITEM_COL_MAP.put("toplam ozkaynaklar",                 FinancialItemKey.OZKAYNAKLAR);
        ITEM_COL_MAP.put("ozkaynaklar",                        FinancialItemKey.OZKAYNAKLAR);
        ITEM_COL_MAP.put("toplam varliklar",                   FinancialItemKey.TOPLAM_VARLIKLAR);
        ITEM_COL_MAP.put("hasilat",                            FinancialItemKey.HASILAT);
        ITEM_COL_MAP.put("net donem kari zarari",              FinancialItemKey.NET_DONEM_KARI);
        ITEM_COL_MAP.put("net donem kari",                     FinancialItemKey.NET_DONEM_KARI);
    }

    public KapFinancialExcelParser(CompanyProfileRepository profileRepository,
                                   MarketInstrumentRepository instrumentRepository) {
        this.profileRepository = profileRepository;
        this.instrumentRepository = instrumentRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public record KapParseAttempt(boolean recognized, ManualFinancialCsvReader.CsvReadResult result) {}

    /**
     * Tek geçişte hem formatı tanır hem parse eder.
     * recognized=false ise dosya KAP geniş formatında değildir.
     */
    public KapParseAttempt tryParse(MultipartFile file) {
        ManualFinancialCsvReader.CsvReadResult result = parseInternal(file);
        return result == null
                ? new KapParseAttempt(false, null)
                : new KapParseAttempt(true, result);
    }

    /** Geriye dönük uyumluluk için. */
    public ManualFinancialCsvReader.CsvReadResult parse(MultipartFile file) {
        ManualFinancialCsvReader.CsvReadResult r = parseInternal(file);
        if (r == null) {
            List<ManualFinancialImportError> errors = List.of(
                    err("KAP geniş format başlık satırı bulunamadı (ilk 10 satır tarandı)."));
            return new ManualFinancialCsvReader.CsvReadResult(List.of(), errors);
        }
        return r;
    }

    // -------------------------------------------------------------------------
    // Core parsing
    // -------------------------------------------------------------------------

    private ManualFinancialCsvReader.CsvReadResult parseInternal(MultipartFile file) {
        List<ManualFinancialImportRow> rows = new ArrayList<>();
        List<ManualFinancialImportError> errors = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);

            // 1. Başlık satırını bul
            int hIdx = findHeaderRow(sheet);
            if (hIdx < 0) return null;  // bu parser için değil

            WideColIndex cols = buildColIndex(sheet.getRow(hIdx));
            String colErr = validateColIndex(cols);
            if (colErr != null) {
                errors.add(err(colErr));
                return new ManualFinancialCsvReader.CsvReadResult(rows, errors);
            }
            logFoundItemCols(cols, sheet.getRow(hIdx));

            // 2. Şirket adı → ticker index (DB'den)
            Map<String, String> nameIndex = buildCompanyNameIndex();

            // 3. Veri satırlarını parse et
            List<RawWideRow> rawRows = new ArrayList<>();
            for (int i = hIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String company = cellStr(row, cols.company);
                if (company == null || company.isBlank()) continue;

                RawWideRow raw = parseDataRow(i + 1, row, cols, nameIndex, errors);
                if (raw != null) rawRows.add(raw);
            }

            // 4. Konsolide tercih + long format'a çevir
            rows = applyConsolidationAndExpand(rawRows);

            // 5. Aynı şirket için duplicate hataları teke indir
            errors = deduplicateErrors(errors);

            log.info("kap.wide.parse.done file={} dataRows={} outputRows={} errors={}",
                    file.getOriginalFilename(), rawRows.size(), rows.size(), errors.size());

        } catch (Exception e) {
            log.error("kap.wide.parse.failed file={}", file.getOriginalFilename(), e);
            errors.add(err("XLSX okunamadı: " + e.getMessage()));
        }

        return new ManualFinancialCsvReader.CsvReadResult(rows, errors);
    }

    // -------------------------------------------------------------------------
    // Format detection
    // -------------------------------------------------------------------------

    private int findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(9, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (isWideHeaderRow(row)) return i;
        }
        return -1;
    }

    private boolean isWideHeaderRow(Row row) {
        int matched = 0;
        for (int c = 0; c <= row.getLastCellNum(); c++) {
            String norm = normTr(cellStr(row.getCell(c)));
            if (WIDE_REQUIRED.contains(norm)) matched++;
        }
        // Ayrıca en az bir finansal kalem kolonu olmalı
        if (matched < WIDE_REQUIRED.size()) return false;
        for (int c = 0; c <= row.getLastCellNum(); c++) {
            String norm = normTr(cellStr(row.getCell(c)));
            if (ITEM_COL_MAP.containsKey(norm)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Column index
    // -------------------------------------------------------------------------

    private WideColIndex buildColIndex(Row headerRow) {
        WideColIndex idx = new WideColIndex();
        for (int c = 0; c <= headerRow.getLastCellNum(); c++) {
            String norm = normTr(cellStr(headerRow.getCell(c)));
            if (norm.isBlank()) continue;

            if (idx.company < 0     && norm.equals("sirket"))                   idx.company     = c;
            if (idx.tickerCol < 0   &&
                    matchesAny(norm, TICKER_COLS))           idx.tickerCol   = c;
            if (idx.bildirimId < 0  && norm.startsWith("bildirim id"))          idx.bildirimId  = c;
            if (idx.publishDate < 0 && norm.startsWith("bildirim yayin"))       idx.publishDate = c;
            if (idx.year < 0        && norm.equals("yil"))                      idx.year        = c;
            if (idx.period < 0      && norm.equals("periyot"))                  idx.period      = c;
            if (idx.consol < 0      && norm.startsWith("finansal tablo nitel")) idx.consol      = c;
            if (idx.currency < 0    && norm.startsWith("sunum para"))           idx.currency    = c;

            FinancialItemKey itemKey = resolveItemCol(norm);
            if (itemKey != null) idx.itemCols.putIfAbsent(itemKey, c);
        }
        return idx;
    }

    private FinancialItemKey resolveItemCol(String norm) {
        if (ITEM_COL_MAP.containsKey(norm)) return ITEM_COL_MAP.get(norm);
        // Prefix match — handles slight variations
        for (Map.Entry<String, FinancialItemKey> e : ITEM_COL_MAP.entrySet()) {
            if (norm.startsWith(e.getKey()) || e.getKey().startsWith(norm)) return e.getValue();
        }
        return null;
    }

    private void logFoundItemCols(WideColIndex cols, Row headerRow) {
        cols.itemCols.forEach((itemKey, colIdx) ->
                log.info("kap.wide.itemCol.mapped itemKey={} excelHeader='{}'", itemKey, cellStr(headerRow.getCell(colIdx))));
        for (FinancialItemKey key : new FinancialItemKey[]{FinancialItemKey.HASILAT, FinancialItemKey.NET_DONEM_KARI}) {
            if (!cols.itemCols.containsKey(key)) {
                log.warn("kap.wide.itemCol.missing itemKey={}: dosyada kolon bulunamadi", key);
            }
        }
    }

    private String validateColIndex(WideColIndex cols) {
        List<String> missing = new ArrayList<>();
        if (cols.company < 0) missing.add("Şirket");
        if (cols.year < 0)    missing.add("Yıl");
        if (cols.period < 0)  missing.add("Periyot");
        if (cols.itemCols.isEmpty()) missing.add("Finansal kalem kolonları (Toplam Kaynaklar, Hasılat vb.)");
        return missing.isEmpty() ? null : "KAP geniş format zorunlu kolon eksik: " + String.join(", ", missing);
    }

    // -------------------------------------------------------------------------
    // Company name → ticker matching
    // -------------------------------------------------------------------------

    /**
     * Şirket eşleşme indeksi: normalizedName → ticker.
     * Zayıf profil (company_name == ticker_code) durumunda market_instruments.instrument_name
     * ek girdi olarak eklenir.
     */
    Map<String, String> buildCompanyNameIndex() {
        // instrument_name tablosunu ticker → name olarak yükle
        Map<String, String> instrNameByTicker = instrumentRepository
                .findAllByInstrumentType(InstrumentType.STOCK)
                .stream()
                .collect(Collectors.toMap(
                        mi -> mi.getInstrumentCode().toUpperCase(Locale.ROOT),
                        MarketInstrument::getInstrumentName,
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, String> idx = new LinkedHashMap<>();
        for (CompanyProfile cp : profileRepository.findByActiveTrue()) {
            String ticker = cp.getTickerCode().toUpperCase(Locale.ROOT);

            // Birincil: company_name
            String normName = normalizeForMatch(cp.getCompanyName());
            if (!normName.isBlank()) idx.putIfAbsent(normName, ticker);

            // Zayıf profil kontrolü: company_name sadece ticker_code'dan oluşuyorsa
            boolean isWeak = cp.getCompanyName() != null
                    && cp.getCompanyName().trim().equalsIgnoreCase(cp.getTickerCode().trim());
            if (isWeak) {
                String instrName = instrNameByTicker.get(ticker);
                if (instrName != null) {
                    String normInstr = normalizeForMatch(instrName);
                    // Anlamlı bir ad ise (sadece ticker değilse) ek girdi ekle
                    if (!normInstr.isBlank() && !normInstr.equalsIgnoreCase(ticker.toLowerCase(Locale.ROOT))) {
                        idx.putIfAbsent(normInstr, ticker);
                        log.debug("kap.index.weakFallback ticker={} instrName='{}'", ticker, normInstr);
                    }
                }
            }
        }
        return idx;
    }

    /** Şirket adı aynı olan hataları teke indirir (N satır × 1 şirket → 1 hata). */
    private List<ManualFinancialImportError> deduplicateErrors(List<ManualFinancialImportError> errors) {
        Set<String> seen = new LinkedHashSet<>();
        return errors.stream()
                .filter(e -> seen.add(e.getFieldName() + "|" + e.getMessage()))
                .collect(Collectors.toList());
    }

    private String resolveTicker(String kapName, Map<String, String> idx) {
        if (kapName == null || kapName.isBlank()) return null;
        String normKap = normalizeForMatch(kapName);
        if (normKap.isBlank()) return null;

        // 1. Tam normalize eşleşme
        if (idx.containsKey(normKap)) return idx.get(normKap);

        // 2. DB adı KAP adının içinde geçiyor (DB daha kısa — en uzun olanı seç)
        String bestTicker = null;
        int bestLen = 0;
        for (Map.Entry<String, String> e : idx.entrySet()) {
            String normDb = e.getKey();
            if (normDb.length() >= 5 && normKap.contains(normDb) && normDb.length() > bestLen) {
                bestTicker = e.getValue();
                bestLen = normDb.length();
            }
        }
        if (bestTicker != null) return bestTicker;

        // 3. KAP adı DB adının içinde geçiyor (DB daha uzun)
        for (Map.Entry<String, String> e : idx.entrySet()) {
            String normDb = e.getKey();
            if (normKap.length() >= 5 && normDb.contains(normKap)) return e.getValue();
        }

        // Eşleşme bulunamadı — hangi normalizasyonların olduğunu logla
        logNoMatch(kapName, normKap, idx);
        return null;
    }

    /** Eşleşemeyen şirket için debug bilgisi: normKap + en yakın 5 DB kaydı */
    private void logNoMatch(String kapName, String normKap, Map<String, String> idx) {
        log.warn("kap.company.nomatch originalName='{}' normalizedKapName='{}'", kapName, normKap);
        String[] kapTokens = normKap.split(" ");
        idx.entrySet().stream()
                .sorted((a, b) -> sharedTokens(kapTokens, b.getKey()) - sharedTokens(kapTokens, a.getKey()))
                .limit(5)
                .forEach(e -> log.warn(
                        "kap.company.candidate ticker={} normalizedDbName='{}' sharedTokens={}",
                        e.getValue(), e.getKey(), sharedTokens(kapTokens, e.getKey())));
        log.warn("kap.company.nomatch.reason: tam eslesme yok; DB adi KAP adinin icinde gecmiyor; KAP adi DB adinin icinde gecmiyor.");
    }

    private boolean matchesAny(String normalizedHeader, List<String> aliases) {
        for (String alias : aliases) {
            if (normalizedHeader.equals(alias) || normalizedHeader.startsWith(alias)) return true;
        }
        return false;
    }

    private int sharedTokens(String[] kapTokens, String normDb) {
        int count = 0;
        for (String t : kapTokens) {
            if (t.length() >= 3 && normDb.contains(t)) count++;
        }
        return count;
    }

    /**
     * Şirket adı karşılaştırması için normalize:
     * - Türkçe → Latin
     * - Küçük harf
     * - Hukuki ekleri kaldır (a.ş., a.s., t.a.ş., a.o.)
     * - Noktalama → boşluk
     * - Çoklu boşluk → tek boşluk
     */
    private String normalizeForMatch(String name) {
        if (name == null) return "";
        String s = normTr(name);
        // Hukuki ekleri YALNIZCA string sonunda kaldır — \b sorunu yaratmaz,
        // kelimenin içindeki "as" gibi parçaları etkilemez.
        s = s.replaceAll("\\s+t\\.?\\s*a\\.?\\s*s\\.?\\s*$", "");  // t.a.s. / t.a.ş. / tas
        s = s.replaceAll("\\s+a\\.?\\s*[so]\\.?\\s*$", "");          // a.s. / a.ş. / a.o. / as / ao
        // Kalan noktalama → boşluk, çoklu boşluk → tekil
        s = s.replaceAll("[^a-z0-9 ]", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    // -------------------------------------------------------------------------
    // Row parsing
    // -------------------------------------------------------------------------

    private RawWideRow parseDataRow(int lineNum, Row row, WideColIndex cols,
                                    Map<String, String> nameIndex,
                                    List<ManualFinancialImportError> errors) {
        String kapName = cellStr(row, cols.company);

        // Önce: doğrudan ticker kolonu varsa kullan (bazı KAP formatlarında "Hisse Kodu" olabilir)
        // Sonra: normalized company_name eşleştirmesi
        String ticker = null;
        if (cols.tickerCol >= 0) {
            String direct = cellStr(row, cols.tickerCol).trim().toUpperCase(Locale.ROOT);
            if (!direct.isBlank()) ticker = direct;
        }
        if (ticker == null) {
            ticker = resolveTicker(kapName, nameIndex);
        }
        if (ticker == null) {
            errors.add(rowErr(lineNum, null, "sirket",
                    "Şirket eşleşmedi: " + kapName));
            return null;
        }

        // Yıl
        String yearStr = cellStr(row, cols.year).replaceAll("[^0-9]", "");
        int year;
        try {
            year = Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            errors.add(rowErr(lineNum, ticker, "yil", "Yıl parse edilemedi: " + cellStr(row, cols.year)));
            return null;
        }

        // Periyot
        ReportType reportType = parsePeriyot(cellStr(row, cols.period));
        if (reportType == null) {
            errors.add(rowErr(lineNum, ticker, "periyot",
                    "Periyot parse edilemedi. Beklenen: 1/2/3/4. Gelen: " + cellStr(row, cols.period)));
            return null;
        }

        // Konsolide
        boolean consolidated = isConsolidated(cols.consol >= 0 ? cellStr(row, cols.consol) : "");

        // Para birimi ve çarpan
        CurrencyUnit cu = parseCurrencyUnit(cols.currency >= 0 ? cellStr(row, cols.currency) : "");

        // Yayın tarihi (opsiyonel)
        String publishedAt = cols.publishDate >= 0
                ? normalizeDateStr(cellStr(row, cols.publishDate))
                : null;

        // Kaynak URL: Bildirim ID varsa onu kullan
        String sourceUrl = "kap://xlsx-import";
        if (cols.bildirimId >= 0) {
            String bid = cellStr(row, cols.bildirimId);
            if (!bid.isBlank()) sourceUrl = "kap://bildirim/" + bid.trim();
        }

        // Finansal kalem değerleri
        Map<FinancialItemKey, BigDecimal> values = new LinkedHashMap<>();
        for (Map.Entry<FinancialItemKey, Integer> e : cols.itemCols.entrySet()) {
            String raw = cellStr(row, e.getValue());
            if (raw.isBlank()) {
                log.debug("kap.wide.blankValue lineNum={} ticker={} itemKey={}", lineNum, ticker, e.getKey());
                continue;
            }
            BigDecimal val = parseValue(raw);
            if (val != null) {
                values.putIfAbsent(e.getKey(), val);
            } else {
                errors.add(rowErr(lineNum, ticker, e.getKey().name(),
                        "Değer parse edilemedi: " + raw));
            }
        }

        if (values.isEmpty()) {
            log.debug("kap.wide.emptyValues lineNum={} ticker={} year={} period={}",
                    lineNum, ticker, year, reportType);
            return null;
        }

        return new RawWideRow(lineNum, ticker, year, reportType,
                consolidated, cu.currency(), cu.multiplier(), publishedAt, sourceUrl, values);
    }

    // -------------------------------------------------------------------------
    // Consolidation preference + wide → long expansion
    // -------------------------------------------------------------------------

    private List<ManualFinancialImportRow> applyConsolidationAndExpand(List<RawWideRow> rawRows) {
        // Group by (ticker, year, reportType)
        Map<GroupKey, List<RawWideRow>> groups = new LinkedHashMap<>();
        for (RawWideRow r : rawRows) {
            groups.computeIfAbsent(new GroupKey(r.ticker(), r.year(), r.reportType()), k -> new ArrayList<>()).add(r);
        }

        List<ManualFinancialImportRow> result = new ArrayList<>();
        for (Map.Entry<GroupKey, List<RawWideRow>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<RawWideRow> group = entry.getValue();

            RawWideRow selected;
            if (group.size() == 1) {
                selected = group.get(0);
            } else {
                RawWideRow consol = group.stream().filter(RawWideRow::consolidated).findFirst().orElse(null);
                if (consol != null) {
                    selected = consol;
                    log.info("kap.wide.consolidation.preferred ticker={} year={} period={} skipped={}",
                            key.ticker(), key.year(), key.reportType(), group.size() - 1);
                } else {
                    selected = group.get(0);
                    log.info("kap.wide.consolidation.noConsolidate ticker={} year={} period={} using=first",
                            key.ticker(), key.year(), key.reportType());
                }
            }

            result.addAll(expandRow(selected));
        }
        return result;
    }

    private List<ManualFinancialImportRow> expandRow(RawWideRow raw) {
        List<ManualFinancialImportRow> rows = new ArrayList<>();
        for (Map.Entry<FinancialItemKey, BigDecimal> e : raw.values().entrySet()) {
            rows.add(new ManualFinancialImportRow(
                    raw.lineNumber(),
                    raw.ticker(),
                    null,
                    String.valueOf(raw.year()),
                    raw.reportType().name(),
                    raw.publishedAt(),
                    raw.sourceUrl(),
                    e.getKey().name(),
                    e.getKey().name(),                          // rawLabel: key adını kullan
                    e.getValue().toPlainString(),
                    raw.currency(),
                    String.valueOf(raw.unitMultiplier()),
                    ""
            ));
        }
        return rows;
    }

    // -------------------------------------------------------------------------
    // Period parsing — 1→Q1, 2→Q2, 3→Q3, 4→ANNUAL
    // -------------------------------------------------------------------------

    private ReportType parsePeriyot(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        return switch (s) {
            case "1" -> ReportType.Q1;
            case "2" -> ReportType.Q2;
            case "3" -> ReportType.Q3;
            case "4" -> ReportType.ANNUAL;
            default  -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Currency / unit multiplier — "1.000 TL" → (TRY, 1000)
    // -------------------------------------------------------------------------

    private record CurrencyUnit(String currency, int multiplier) {}

    private CurrencyUnit parseCurrencyUnit(String raw) {
        if (raw == null || raw.isBlank()) return new CurrencyUnit("TRY", 1);
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        String currency = upper.contains("USD") ? "USD" : "TRY";
        // Sadece rakamları al (nokta ve virgül = binlik ayraç veya yok)
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return new CurrencyUnit(currency, 1);
        try {
            long num = Long.parseLong(digits);
            int mult = (num > 0 && num <= Integer.MAX_VALUE) ? (int) num : 1;
            return new CurrencyUnit(currency, mult);
        } catch (NumberFormatException e) {
            return new CurrencyUnit(currency, 1);
        }
    }

    // -------------------------------------------------------------------------
    // Value parsing — Türkçe binlik ayraç desteği
    // -------------------------------------------------------------------------

    private BigDecimal parseValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String s = raw.trim().replace(" ", "");
            // Türkçe format: 1.234.567,89 → nokta=binlik, virgül=ondalık
            if (s.contains(",")) {
                s = s.replace(".", "").replace(",", ".");
            } else {
                // Sadece nokta var — binlik ayraç mı ondalık mı?
                // 3'lü gruplar: binlik ayraç. Değilse: ondalık nokta.
                if (s.matches("-?\\d{1,3}(\\.\\d{3})+")) {
                    s = s.replace(".", "");
                }
                // Aksi halde olduğu gibi bırak
            }
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Date normalization — "dd.MM.yyyy" → "YYYY-MM-DD"
    // -------------------------------------------------------------------------

    /**
     * KAP tarih string'ini YYYY-MM-DD formatına çevirir.
     * Parse edilemiyorsa null döner — published_at opsiyonel olduğundan import devam eder.
     *
     * Desteklenen formatlar:
     *   yyyy-MM-dd                 → doğrudan döner
     *   yyyy-MM-dd HH:mm:ss        → sadece tarih kısmı alınır
     *   dd.MM.yyyy                 → çevrilir
     *   dd.MM.yyyy HH:mm:ss        → tarih kısmı çevrilir
     *   dd/MM/yyyy                 → çevrilir
     *   dd/MM/yyyy HH:mm:ss        → tarih kısmı çevrilir
     *   Excel numeric date         → getCellStringValue() zaten ISO döndürür
     */
    private String normalizeDateStr(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();

        // ISO başlangıçlı: yyyy-MM-dd veya yyyy-MM-dd HH:mm:ss
        if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return s.substring(0, 10);
        }

        // Noktalı Türk formatı: dd.MM.yyyy veya dd.MM.yyyy HH:mm:ss
        if (s.matches("\\d{2}\\.\\d{2}\\.\\d{4}.*")) {
            return s.substring(6, 10) + "-" + s.substring(3, 5) + "-" + s.substring(0, 2);
        }

        // Slash formatı: dd/MM/yyyy veya dd/MM/yyyy HH:mm:ss
        if (s.matches("\\d{2}/\\d{2}/\\d{4}.*")) {
            return s.substring(6, 10) + "-" + s.substring(3, 5) + "-" + s.substring(0, 2);
        }

        // Tanınmayan format → null (published_at opsiyonel, hata üretilmez)
        log.debug("kap.date.unparseable value='{}'", raw);
        return null;
    }

    // -------------------------------------------------------------------------
    // Consolidation check
    // -------------------------------------------------------------------------

    private boolean isConsolidated(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String norm = normTr(raw.trim());
        if (norm.contains("olmayan") || norm.contains("dahil edilmeyen") || norm.equals("yok")) return false;
        return norm.startsWith("konsolide");
    }

    // -------------------------------------------------------------------------
    // Cell reading
    // -------------------------------------------------------------------------

    private String cellStr(Row row, int col) {
        if (col < 0 || row == null) return "";
        Cell cell = row.getCell(col);
        return cellStr(cell);
    }

    private String cellStr(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    yield String.valueOf((long) d);
                }
                yield BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    // -------------------------------------------------------------------------
    // String helpers
    // -------------------------------------------------------------------------

    private String normTr(String s) {
        if (s == null) return "";
        // 'İ' (U+0130) must be replaced BEFORE toLowerCase(ROOT):
        // ROOT locale maps 'İ' → "i̇" (two chars), so a post-toLowerCase
        // replace('İ','i') never fires. Replace it to plain 'I' first.
        return s
                .replace('İ', 'I')
                .replace('ı', 'i')
                .replace('Â', 'A').replace('â', 'a')  // KAP: "Kârı" → "Kari"
                .replace('Î', 'I').replace('î', 'i')
                .replace('Û', 'U').replace('û', 'u')
                .trim()
                .toLowerCase(Locale.ROOT)   // Ş→ş, Ğ→ğ, Ü→ü, Ö→ö, Ç→ç handled correctly here
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replaceAll("\\s+", " ");
    }

    // -------------------------------------------------------------------------
    // Error helpers
    // -------------------------------------------------------------------------

    private ManualFinancialImportError err(String message) {
        return ManualFinancialImportError.builder().fieldName("file").message(message).build();
    }

    private ManualFinancialImportError rowErr(int line, String ticker, String field, String message) {
        return ManualFinancialImportError.builder()
                .lineNumber(line).tickerCode(ticker).fieldName(field).message(message).build();
    }

    // -------------------------------------------------------------------------
    // Internal records
    // -------------------------------------------------------------------------

    private record RawWideRow(
            int lineNumber,
            String ticker,
            int year,
            ReportType reportType,
            boolean consolidated,
            String currency,
            int unitMultiplier,
            String publishedAt,
            String sourceUrl,
            Map<FinancialItemKey, BigDecimal> values
    ) {}

    private record GroupKey(String ticker, int year, ReportType reportType) {}

    private static final class WideColIndex {
        int company     = -1;
        int tickerCol   = -1;   // opsiyonel — bazı KAP formatlarında hisse kodu kolonu olabilir
        int bildirimId  = -1;
        int publishDate = -1;
        int year        = -1;
        int period      = -1;
        int consol      = -1;
        int currency    = -1;
        Map<FinancialItemKey, Integer> itemCols = new LinkedHashMap<>();
    }
}
