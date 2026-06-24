package com.emrehalli.financeportal.market.service.mock;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds realistic mock data for VİOP (FUTURES) and bond/interest-rate (BOND)
 * instruments that are absent from live providers.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Idempotent: calling seed() multiple times does not create duplicate records.</li>
 *   <li>Deterministic: same instrument code always produces the same price series.</li>
 *   <li>Isolation: never touches real provider data (FX, CRYPTO, STOCK, FUND, etc.).</li>
 *   <li>Clean-up: removes legacy mock instruments from previous seed formats before re-seeding.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MockDerivativesSeedService {

    private static final SourceName FUTURES_SOURCE = SourceName.BIST;
    private static final SourceName BOND_SOURCE = SourceName.TCMB;

    private static final String META = "||META||";
    private static final int HISTORY_YEARS = 3;
    private static final int BATCH_CHUNK = 500;
    private static final BigDecimal BD_100 = BigDecimal.valueOf(100);

    // Mean-reversion strength: price pulled 4% toward base per business day
    private static final double MEAN_REVERSION_SPEED = 0.04;
    // Price hard-clamp at ±18% of base; keeps realistic bands without wild drifts
    private static final double PRICE_FLOOR_RATIO = 0.82;
    private static final double PRICE_CEIL_RATIO  = 1.18;

    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceRepository priceRepository;
    private final JdbcTemplate jdbc;

    // -------------------------------------------------------------------------
    // Instrument definitions — FUT_<UNDERLYING>_<YYYYMM> format
    // -------------------------------------------------------------------------

    private record FuturesDef(String code, String name, LocalDate expiry, double base, double vol) {}

    private record BondDef(String code, String name, LocalDate maturity, double base, double vol) {}

    /**
     * Old codes written by the previous seed format — cleaned up on every seed run.
     * Ordering does not matter; all are deleted before new instruments are created.
     */
    private static final List<String> LEGACY_FUTURES_CODES = List.of(
            "FXU0300626","FXU0300826","FXU0301026","FXU0301226",
            "FUSDTRY0626","FUSDTRY0826","FUSDTRY1026",
            "FEURTRY0626","FEURTRY0826","FEURTRY1026",
            "FXAUTRY0626","FXAUTRY0826","FXAUTRY1026",
            "FTHYAO0626","FASELS0626","FEREGL0626","FAKBNK0626","FGARAN0626",
            "FTUPRS0626","FKCHOL0626","FSAHOL0626","FBIMAS0626","FKOZAL0626"
    );

    private static final List<FuturesDef> FUTURES_DEFS = List.of(
            // BIST-30 endeks vadeli — hedef bant 11.000-15.000
            new FuturesDef("FUT_XU030_202606", "BIST 30 Endeks Vadeli Haziran 2026", LocalDate.of(2026,  6, 30), 12500.0, 0.010),
            new FuturesDef("FUT_XU030_202608", "BIST 30 Endeks Vadeli Ağustos 2026", LocalDate.of(2026,  8, 28), 13000.0, 0.010),
            new FuturesDef("FUT_XU030_202610", "BIST 30 Endeks Vadeli Ekim 2026",    LocalDate.of(2026, 10, 30), 13200.0, 0.010),
            new FuturesDef("FUT_XU030_202612", "BIST 30 Endeks Vadeli Aralık 2026",  LocalDate.of(2026, 12, 18), 13500.0, 0.010),
            // Döviz vadeli
            new FuturesDef("FUT_USDTRY_202606", "USD/TRY Vadeli Haziran 2026",  LocalDate.of(2026,  6, 30),  38.50, 0.008),
            new FuturesDef("FUT_USDTRY_202608", "USD/TRY Vadeli Ağustos 2026",  LocalDate.of(2026,  8, 28),  39.20, 0.008),
            new FuturesDef("FUT_USDTRY_202610", "USD/TRY Vadeli Ekim 2026",     LocalDate.of(2026, 10, 30),  40.10, 0.008),
            new FuturesDef("FUT_EURTRY_202606", "EUR/TRY Vadeli Haziran 2026",  LocalDate.of(2026,  6, 30),  42.30, 0.009),
            new FuturesDef("FUT_EURTRY_202608", "EUR/TRY Vadeli Ağustos 2026",  LocalDate.of(2026,  8, 28),  43.15, 0.009),
            new FuturesDef("FUT_EURTRY_202610", "EUR/TRY Vadeli Ekim 2026",     LocalDate.of(2026, 10, 30),  44.20, 0.009),
            // Altın vadeli
            new FuturesDef("FUT_XAUTRY_202606", "Gram Altın/TL Vadeli Haziran 2026", LocalDate.of(2026,  6, 30), 3450.0, 0.010),
            new FuturesDef("FUT_XAUTRY_202608", "Gram Altın/TL Vadeli Ağustos 2026", LocalDate.of(2026,  8, 28), 3520.0, 0.010),
            new FuturesDef("FUT_XAUTRY_202610", "Gram Altın/TL Vadeli Ekim 2026",    LocalDate.of(2026, 10, 30), 3600.0, 0.010),
            // Hisse vadeli
            new FuturesDef("FUT_THYAO_202606", "THYAO Vadeli Haziran 2026", LocalDate.of(2026, 6, 30), 285.0, 0.015),
            new FuturesDef("FUT_ASELS_202606", "ASELS Vadeli Haziran 2026", LocalDate.of(2026, 6, 30), 125.0, 0.015),
            new FuturesDef("FUT_EREGL_202606", "EREGL Vadeli Haziran 2026", LocalDate.of(2026, 6, 30),  48.0, 0.014),
            new FuturesDef("FUT_AKBNK_202606", "AKBNK Vadeli Haziran 2026", LocalDate.of(2026, 6, 30),  52.0, 0.014),
            new FuturesDef("FUT_GARAN_202606", "GARAN Vadeli Haziran 2026", LocalDate.of(2026, 6, 30),  95.0, 0.014),
            new FuturesDef("FUT_TUPRS_202606", "TUPRS Vadeli Haziran 2026", LocalDate.of(2026, 6, 30), 185.0, 0.013),
            new FuturesDef("FUT_KCHOL_202606", "KCHOL Vadeli Haziran 2026", LocalDate.of(2026, 6, 30), 145.0, 0.013),
            new FuturesDef("FUT_SAHOL_202606", "SAHOL Vadeli Haziran 2026", LocalDate.of(2026, 6, 30),  95.0, 0.013),
            new FuturesDef("FUT_BIMAS_202606", "BIMAS Vadeli Haziran 2026", LocalDate.of(2026, 6, 30), 420.0, 0.012),
            new FuturesDef("FUT_KOZAL_202606", "KOZAL Vadeli Haziran 2026", LocalDate.of(2026, 6, 30), 285.0, 0.016)
    );

    private static final List<BondDef> BOND_DEFS = List.of(
            // Devlet tahvilleri
            new BondDef("TR2Y",  "Türkiye 2 Yıllık Gösterge Tahvil",    LocalDate.of(2028,  6,  8), 38.50, 0.005),
            new BondDef("TR5Y",  "Türkiye 5 Yıllık Devlet Tahvili",      LocalDate.of(2031,  6,  8), 36.80, 0.004),
            new BondDef("TR10Y", "Türkiye 10 Yıllık Devlet Tahvili",     LocalDate.of(2036,  6,  8), 33.50, 0.004),
            new BondDef("TR20Y", "Türkiye 20 Yıllık Devlet Tahvili",     LocalDate.of(2046,  6,  8), 30.20, 0.003),
            // Hazine bonoları
            new BondDef("TRB3M",  "Türkiye 3 Aylık Hazine Bonosu",  LocalDate.of(2026,  9,  8), 47.80, 0.003),
            new BondDef("TRB6M",  "Türkiye 6 Aylık Hazine Bonosu",  LocalDate.of(2026, 12,  8), 46.50, 0.003),
            new BondDef("TRB9M",  "Türkiye 9 Aylık Hazine Bonosu",  LocalDate.of(2027,  3,  8), 45.20, 0.003),
            new BondDef("TRB12M", "Türkiye 12 Aylık Hazine Bonosu", LocalDate.of(2027,  6,  8), 44.10, 0.003),
            // Eurobondlar
            new BondDef("TR2030", "Türkiye Eurobond 2030", LocalDate.of(2030, 1, 1),  8.50, 0.003),
            new BondDef("TR2033", "Türkiye Eurobond 2033", LocalDate.of(2033, 1, 1),  8.20, 0.003),
            new BondDef("TR2035", "Türkiye Eurobond 2035", LocalDate.of(2035, 1, 1),  8.00, 0.003),
            new BondDef("TR2038", "Türkiye Eurobond 2038", LocalDate.of(2038, 1, 1),  7.80, 0.003),
            new BondDef("TR2040", "Türkiye Eurobond 2040", LocalDate.of(2040, 1, 1),  7.60, 0.003),
            new BondDef("TR2043", "Türkiye Eurobond 2043", LocalDate.of(2043, 1, 1),  7.40, 0.003),
            new BondDef("TR2045", "Türkiye Eurobond 2045", LocalDate.of(2045, 1, 1),  7.25, 0.003),
            new BondDef("TR2047", "Türkiye Eurobond 2047", LocalDate.of(2047, 1, 1),  7.10, 0.003),
            new BondDef("TR2053", "Türkiye Eurobond 2053", LocalDate.of(2053, 1, 1),  6.90, 0.003),
            // Faiz göstergeleri
            new BondDef("POLICYRATETR",  "TCMB Politika Faizi",                      null, 47.50, 0.002),
            new BondDef("TLREF",         "Türk Lirası Gecelik Referans Faiz Oranı",  null, 46.80, 0.002),
            new BondDef("ONBORROWINGTR", "TCMB Gecelik Borçlanma Faizi",             null, 46.50, 0.002),
            new BondDef("ONLENDINGTR",   "TCMB Gecelik Borç Verme Faizi",            null, 48.50, 0.002),
            new BondDef("WACFTR",        "TCMB Ağırlıklı Ortalama Fonlama Maliyeti", null, 47.20, 0.002)
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public MockSeedResult seed() {
        log.info("[MockDerivatives] Starting seed: {} futures, {} bonds", FUTURES_DEFS.size(), BOND_DEFS.size());

        cleanupLegacyMockInstruments();

        int priceCount = 0;
        int historyCount = 0;

        for (FuturesDef def : FUTURES_DEFS) {
            MarketInstrument instrument = findOrCreateFutures(def);
            PriceSeries series = generateSeries(def.code(), def.base(), def.vol());
            historyCount += insertHistory(instrument, FUTURES_SOURCE, series);
            priceCount += saveCurrentPrice(instrument, FUTURES_SOURCE, series);
            updateFuturesName(instrument, def, series);
        }

        for (BondDef def : BOND_DEFS) {
            MarketInstrument instrument = findOrCreateBond(def);
            PriceSeries series = generateSeries(def.code(), def.base(), def.vol());
            historyCount += insertHistory(instrument, BOND_SOURCE, series);
            priceCount += saveCurrentPrice(instrument, BOND_SOURCE, series);
        }

        log.info("[MockDerivatives] Seed complete: futures={}, bonds={}, prices={}, history={}",
                FUTURES_DEFS.size(), BOND_DEFS.size(), priceCount, historyCount);
        return new MockSeedResult(FUTURES_DEFS.size(), BOND_DEFS.size(), priceCount, historyCount);
    }

    // -------------------------------------------------------------------------
    // Legacy cleanup — removes old-format codes from previous seed runs
    // -------------------------------------------------------------------------

    private void cleanupLegacyMockInstruments() {
        int removed = 0;
        for (String code : LEGACY_FUTURES_CODES) {
            jdbc.update("DELETE FROM market_price_history WHERE instrument_id IN " +
                    "(SELECT id FROM market_instruments WHERE source_name = 'BIST' AND instrument_code = ?)", code);
            jdbc.update("DELETE FROM market_prices WHERE instrument_id IN " +
                    "(SELECT id FROM market_instruments WHERE source_name = 'BIST' AND instrument_code = ?)", code);
            int n = jdbc.update("DELETE FROM market_instruments WHERE source_name = 'BIST' AND instrument_code = ?", code);
            removed += n;
        }
        if (removed > 0) {
            log.info("[MockDerivatives] Removed {} legacy FUTURES instruments", removed);
        }
    }

    // -------------------------------------------------------------------------
    // Instrument find-or-create
    // -------------------------------------------------------------------------

    private MarketInstrument findOrCreateFutures(FuturesDef def) {
        return instrumentRepository.findByInstrumentCodeAndSourceName(def.code(), FUTURES_SOURCE)
                .orElseGet(() -> {
                    MarketInstrument mi = MarketInstrument.builder()
                            .instrumentCode(def.code())
                            .instrumentName(encodeFuturesName(def.name(), BigDecimal.ZERO, def.expiry()))
                            .instrumentType(InstrumentType.FUTURES)
                            .sourceName(FUTURES_SOURCE)
                            .build();
                    return instrumentRepository.save(mi);
                });
    }

    private MarketInstrument findOrCreateBond(BondDef def) {
        return instrumentRepository.findByInstrumentCodeAndSourceName(def.code(), BOND_SOURCE)
                .orElseGet(() -> {
                    MarketInstrument mi = MarketInstrument.builder()
                            .instrumentCode(def.code())
                            .instrumentName(encodeBondName(def.name(), def.maturity()))
                            .instrumentType(InstrumentType.BOND)
                            .sourceName(BOND_SOURCE)
                            .build();
                    return instrumentRepository.save(mi);
                });
    }

    // -------------------------------------------------------------------------
    // Price series generation — mean-reverting walk, deterministic per code
    // -------------------------------------------------------------------------

    /**
     * Generates OHLC price series seeded from the instrument code's hash.
     * Uses an Ornstein-Uhlenbeck-style mean-reversion so the series stays
     * anchored near {@code basePrice} throughout the 3-year window.
     */
    PriceSeries generateSeries(String code, double basePrice, double dailyVol) {
        Random rng = new Random(deterministicSeed(code));
        LocalDate startDate = LocalDate.now().minusYears(HISTORY_YEARS);
        LocalDate endDate = LocalDate.now();

        List<LocalDate> businessDays = businessDaysBetween(startDate, endDate);
        List<HistoryBar> bars = new ArrayList<>(businessDays.size());

        double price = basePrice;
        double minAllowed = basePrice * PRICE_FLOOR_RATIO;
        double maxAllowed = basePrice * PRICE_CEIL_RATIO;

        for (LocalDate date : businessDays) {
            // Mean-reversion: gently pull price back toward base each period
            double reversion = MEAN_REVERSION_SPEED * (basePrice - price) / basePrice;
            double dailyReturn = reversion + rng.nextGaussian() * dailyVol;
            price = Math.max(minAllowed, Math.min(maxAllowed, price * (1.0 + dailyReturn)));

            double open  = price * (1.0 + rng.nextGaussian() * dailyVol * 0.3);
            double range = Math.abs(rng.nextGaussian() * dailyVol * 0.5);
            double high  = Math.max(open, price) * (1.0 + range);
            double low   = Math.min(open, price) * (1.0 - range);

            open  = Math.max(minAllowed, Math.min(maxAllowed, open));
            high  = Math.max(minAllowed, Math.min(maxAllowed, high));
            low   = Math.max(minAllowed, Math.min(maxAllowed, low));

            bars.add(new HistoryBar(date, scale(open), scale(high), scale(low), scale(price)));
        }

        return new PriceSeries(bars);
    }

    static long deterministicSeed(String code) {
        long h = 0;
        for (char c : code.toCharArray()) {
            h = h * 31 + c;
        }
        return Math.abs(h);
    }

    // -------------------------------------------------------------------------
    // Database persistence
    // -------------------------------------------------------------------------

    private int insertHistory(MarketInstrument instrument, SourceName source, PriceSeries series) {
        if (series.bars().isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO market_price_history
                    (instrument_id, open_price, high_price, low_price, close_price, volume,
                     price_timestamp, interval_type, source_name, created_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, 'ONE_DAY', ?, NOW())
                ON CONFLICT (instrument_id, interval_type, price_timestamp, source_name) DO NOTHING
                """;

        List<HistoryBar> bars = series.bars();
        int inserted = 0;
        for (int offset = 0; offset < bars.size(); offset += BATCH_CHUNK) {
            List<HistoryBar> chunk = bars.subList(offset, Math.min(offset + BATCH_CHUNK, bars.size()));
            List<Object[]> params = chunk.stream().map(bar -> new Object[]{
                    instrument.getId(),
                    bar.open(),
                    bar.high(),
                    bar.low(),
                    bar.close(),
                    Timestamp.from(bar.date().atStartOfDay(ZoneOffset.UTC).toInstant()),
                    source.name()
            }).toList();
            int[] results = jdbc.batchUpdate(sql, params);
            for (int r : results) inserted += r;
        }
        return inserted;
    }

    private int saveCurrentPrice(MarketInstrument instrument, SourceName source, PriceSeries series) {
        priceRepository.save(MarketPrice.builder()
                .instrument(instrument)
                .priceValue(series.lastClose())
                .changeRate(series.dailyChangePercent())
                .priceTimestamp(LocalDateTime.now())
                .sourceName(source)
                .build());
        return 1;
    }

    private void updateFuturesName(MarketInstrument instrument, FuturesDef def, PriceSeries series) {
        instrument.setInstrumentName(encodeFuturesName(def.name(), series.dailyChangePercent(), def.expiry()));
        instrumentRepository.save(instrument);
    }

    // -------------------------------------------------------------------------
    // Metadata encoding — must match FuturesService / BondService conventions
    // -------------------------------------------------------------------------

    private String encodeFuturesName(String name, BigDecimal dailyChange, LocalDate expiry) {
        return name + META
                + (dailyChange != null ? dailyChange.toPlainString() : "") + META
                + (expiry != null ? expiry : "");
    }

    private String encodeBondName(String name, LocalDate maturity) {
        return name + META + (maturity != null ? maturity : "");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static List<LocalDate> businessDaysBetween(LocalDate start, LocalDate end) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Value objects
    // -------------------------------------------------------------------------

    record HistoryBar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {}

    record PriceSeries(List<HistoryBar> bars) {
        BigDecimal lastClose() {
            return bars.isEmpty() ? BigDecimal.ZERO : bars.get(bars.size() - 1).close();
        }

        BigDecimal dailyChangePercent() {
            if (bars.size() < 2) return BigDecimal.ZERO;
            BigDecimal prev = bars.get(bars.size() - 2).close();
            BigDecimal curr = bars.get(bars.size() - 1).close();
            if (prev.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            return curr.subtract(prev)
                    .multiply(BD_100)
                    .divide(prev, 4, RoundingMode.HALF_UP);
        }
    }
}
