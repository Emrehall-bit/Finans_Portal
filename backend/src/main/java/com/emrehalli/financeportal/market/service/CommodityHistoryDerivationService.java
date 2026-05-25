package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.commodity.CommoditySymbolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommodityHistoryDerivationService {

    private static final BigDecimal TROY_OZ_TO_GRAM = new BigDecimal("31.1035");

    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceHistoryRepository historyRepository;
    private final CommoditySymbolRegistry symbolRegistry;

    // Proxy symbols used to measure derivation lag (one per input chain)
    private static final Set<String> DERIVATION_PROXIES = Set.of("GRAM_ALTIN", "GUMUS_GRAM");

    public record DerivationResult(int saved, int skipped) {}

    /**
     * Detects gaps in derived commodity history and fills them.
     * Checks GRAM_ALTIN and GUMUS_GRAM as proxies for the gold and silver chains.
     * Uses the largest detected gap so both chains stay in sync.
     *
     * <p>If no derived history exists yet, returns empty — run the admin backfill endpoint first.</p>
     */
    @Transactional
    public Map<String, DerivationResult> catchUp() {
        int gapDays = computeDerivationGap();
        if (gapDays <= 0) {
            log.debug("CommodityHistoryDerivation catch-up: already up to date");
            return Map.of();
        }
        log.info("CommodityHistoryDerivation catch-up: filling gap. deriveDays={}", gapDays);
        return deriveHistory(gapDays);
    }

    /**
     * Admin / manual backfill: derives history for the given look-back window.
     * Duplicate-safe — rows that already exist are skipped.
     *
     * @param days look-back window in calendar days (e.g. 3650 for 10 years)
     * @return per-symbol saved/skipped counts
     */
    @Transactional
    public Map<String, DerivationResult> deriveHistory(int days) {
        Instant to = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant from = to.minus(days, ChronoUnit.DAYS);

        Map<LocalDate, BigDecimal> goldByDay   = loadDailyHistory("GOLD_USD",   InstrumentType.COMMODITY, SourceName.INTERNAL, from, to);
        Map<LocalDate, BigDecimal> silverByDay  = loadDailyHistory("SILVER_USD", InstrumentType.COMMODITY, SourceName.INTERNAL, from, to);
        Map<LocalDate, BigDecimal> usdTryByDay  = loadUsdTryDailyHistory(from, to);

        if (goldByDay.isEmpty() && silverByDay.isEmpty()) {
            log.warn("CommodityHistoryDerivation: no GOLD_USD or SILVER_USD ONE_DAY history found. days={}", days);
        }
        if (usdTryByDay.isEmpty()) {
            log.warn("CommodityHistoryDerivation: no USDTRY FX ONE_DAY history found. days={}", days);
        }

        Map<String, DerivationResult> results = new LinkedHashMap<>();

        if (!goldByDay.isEmpty() && !usdTryByDay.isEmpty()) {
            results.putAll(deriveGoldHistory(goldByDay, usdTryByDay));
        }

        if (!silverByDay.isEmpty() && !usdTryByDay.isEmpty()) {
            results.put("GUMUS_GRAM", deriveSilverHistory(silverByDay, usdTryByDay));
        }

        results.forEach((symbol, r) ->
                log.info("CommodityHistoryDerivation: symbol={} saved={} skipped={}", symbol, r.saved(), r.skipped()));

        return results;
    }

    // ── Gap detection ──────────────────────────────────────────────────────────

    private int computeDerivationGap() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        int maxGap = 0;

        for (String proxyCode : DERIVATION_PROXIES) {
            Optional<MarketInstrument> instrumentOpt = instrumentRepository
                    .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(proxyCode, InstrumentType.COMMODITY);
            if (instrumentOpt.isEmpty()) {
                continue;
            }

            Optional<MarketPriceHistory> latest = historyRepository
                    .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                            instrumentOpt.get(), IntervalType.ONE_DAY, SourceName.CALCULATED);
            if (latest.isEmpty()) {
                continue; // no history yet — initial backfill needed
            }

            LocalDate lastDate = latest.get().getPriceTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
            int gap = (int) ChronoUnit.DAYS.between(lastDate, yesterday);
            if (gap > maxGap) {
                maxGap = gap;
            }
        }

        return maxGap <= 0 ? 0 : maxGap + 3; // +3 day buffer to align with USDTRY history
    }

    // ── Private derivation ─────────────────────────────────────────────────────

    private Map<String, DerivationResult> deriveGoldHistory(
            Map<LocalDate, BigDecimal> goldByDay,
            Map<LocalDate, BigDecimal> usdTryByDay) {

        List<String> symbols = List.of("GRAM_ALTIN", "CEYREK_ALTIN", "YARIM_ALTIN", "TAM_ALTIN", "CUMHURIYET_ALTINI");
        Map<String, MarketInstrument> instruments = new LinkedHashMap<>();
        Map<String, int[]> counters = new LinkedHashMap<>();
        for (String sym : symbols) {
            instruments.put(sym, resolveInstrument(sym));
            counters.put(sym, new int[]{0, 0});
        }

        TreeSet<LocalDate> matchedDays = new TreeSet<>(goldByDay.keySet());
        matchedDays.retainAll(usdTryByDay.keySet());

        for (LocalDate day : matchedDays) {
            BigDecimal goldUsdOz = goldByDay.get(day);
            BigDecimal usdTry    = usdTryByDay.get(day);
            Instant    ts        = day.atStartOfDay().toInstant(ZoneOffset.UTC);

            BigDecimal gramAltin        = goldUsdOz.multiply(usdTry).divide(TROY_OZ_TO_GRAM, 4, RoundingMode.HALF_UP);
            BigDecimal ceyrekAltin      = gramAltin.multiply(new BigDecimal("1.75")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal yarimAltin       = ceyrekAltin.multiply(new BigDecimal("2")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal tamAltin         = ceyrekAltin.multiply(new BigDecimal("4")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal cumhuriyetAltini = gramAltin.multiply(new BigDecimal("7.216")).setScale(4, RoundingMode.HALF_UP);

            Map<String, BigDecimal> priceMap = Map.of(
                    "GRAM_ALTIN",        gramAltin,
                    "CEYREK_ALTIN",      ceyrekAltin,
                    "YARIM_ALTIN",       yarimAltin,
                    "TAM_ALTIN",         tamAltin,
                    "CUMHURIYET_ALTINI", cumhuriyetAltini
            );

            for (String sym : symbols) {
                int[] c = counters.get(sym);
                if (saveIfAbsent(instruments.get(sym), ts, priceMap.get(sym))) {
                    c[0]++;
                } else {
                    c[1]++;
                }
            }
        }

        Map<String, DerivationResult> results = new LinkedHashMap<>();
        counters.forEach((sym, c) -> results.put(sym, new DerivationResult(c[0], c[1])));
        return results;
    }

    private DerivationResult deriveSilverHistory(
            Map<LocalDate, BigDecimal> silverByDay,
            Map<LocalDate, BigDecimal> usdTryByDay) {

        MarketInstrument instrument = resolveInstrument("GUMUS_GRAM");
        int saved = 0, skipped = 0;

        TreeSet<LocalDate> matchedDays = new TreeSet<>(silverByDay.keySet());
        matchedDays.retainAll(usdTryByDay.keySet());

        for (LocalDate day : matchedDays) {
            BigDecimal silverUsdOz = silverByDay.get(day);
            BigDecimal usdTry      = usdTryByDay.get(day);
            Instant    ts          = day.atStartOfDay().toInstant(ZoneOffset.UTC);

            BigDecimal gumusGram = silverUsdOz.multiply(usdTry).divide(TROY_OZ_TO_GRAM, 4, RoundingMode.HALF_UP);

            if (saveIfAbsent(instrument, ts, gumusGram)) {
                saved++;
            } else {
                skipped++;
            }
        }

        return new DerivationResult(saved, skipped);
    }

    // ── History loading ────────────────────────────────────────────────────────

    private Map<LocalDate, BigDecimal> loadDailyHistory(
            String code, InstrumentType type, SourceName source, Instant from, Instant to) {

        Optional<MarketInstrument> instrumentOpt =
                instrumentRepository.findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, type);

        if (instrumentOpt.isEmpty()) {
            log.warn("CommodityHistoryDerivation: instrument not found in DB. code={}", code);
            return Map.of();
        }

        List<MarketPriceHistory> rows = historyRepository
                .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrumentOpt.get(), IntervalType.ONE_DAY, source, from, to);

        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        for (MarketPriceHistory row : rows) {
            LocalDate  date  = row.getPriceTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal price = row.getClosePrice() != null ? row.getClosePrice() : row.getOpenPrice();
            if (price != null && price.signum() > 0) {
                byDay.putIfAbsent(date, price);
            }
        }

        log.info("CommodityHistoryDerivation: loaded source history. code={} source={} days={}", code, source, byDay.size());
        return byDay;
    }

    private Map<LocalDate, BigDecimal> loadUsdTryDailyHistory(Instant from, Instant to) {
        List<MarketInstrument> allUsd = instrumentRepository
                .findAllByInstrumentTypeAndInstrumentCodeContainingIgnoreCase(InstrumentType.FX, "USD");

        // Keep sell-side USD/TRY — same filter as CommodityService.resolveUsdTry
        List<MarketInstrument> candidates = allUsd.stream()
                .filter(i -> {
                    String code = i.getInstrumentCode().toUpperCase(Locale.ROOT);
                    return code.contains("SELL")
                            && !code.contains("EUR") && !code.contains("GBP")
                            && !code.contains("JPY") && !code.contains("CHF")
                            && !code.contains("AUD") && !code.contains("CAD");
                })
                .toList();

        if (candidates.isEmpty()) {
            log.warn("CommodityHistoryDerivation: no USDTRY FX (SELL) instruments found in DB");
            return Map.of();
        }

        // TCMB-sourced instruments take priority; fall back to all candidates
        List<MarketInstrument> preferred = candidates.stream()
                .filter(i -> i.getSourceName() == SourceName.TCMB)
                .toList();
        if (preferred.isEmpty()) {
            preferred = candidates;
        }

        // Merge daily history from preferred instruments; first value per day wins (TCMB preferred)
        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        for (MarketInstrument instrument : preferred) {
            List<MarketPriceHistory> rows = historyRepository
                    .findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                            instrument, IntervalType.ONE_DAY, from, to);
            for (MarketPriceHistory row : rows) {
                LocalDate  date  = row.getPriceTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
                BigDecimal price = row.getClosePrice() != null ? row.getClosePrice() : row.getOpenPrice();
                if (price != null && price.signum() > 0) {
                    byDay.putIfAbsent(date, price);
                }
            }
        }

        log.info("CommodityHistoryDerivation: loaded USDTRY history. days={} instruments={}", byDay.size(), preferred.size());
        return byDay;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean saveIfAbsent(MarketInstrument instrument, Instant ts, BigDecimal price) {
        if (historyRepository.existsByInstrumentAndIntervalTypeAndPriceTimestamp(
                instrument, IntervalType.ONE_DAY, ts)) {
            return false;
        }
        historyRepository.save(MarketPriceHistory.builder()
                .instrument(instrument)
                .closePrice(price)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.CALCULATED)
                .priceTimestamp(ts)
                .build());
        return true;
    }

    private MarketInstrument resolveInstrument(String code) {
        return instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.COMMODITY)
                .orElseGet(() -> instrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(code)
                        .instrumentName(symbolRegistry.toDisplayName(code))
                        .instrumentType(InstrumentType.COMMODITY)
                        .sourceName(SourceName.CALCULATED)
                        .build()));
    }
}



