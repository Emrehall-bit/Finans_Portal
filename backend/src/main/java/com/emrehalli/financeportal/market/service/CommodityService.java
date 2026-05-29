package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.InstrumentNotFoundException;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.commodity.CommoditySymbolRegistry;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for COMMODITY instrument persistence and retrieval.
 *
 * <p>Source mapping:
 * - BRENT        â†’ YAHOO_FINANCE (visible in API)
 * - GOLD_USD     â†’ INTERNAL (hidden; raw input for gold calculation)
 * - SILVER_USD   â†’ INTERNAL (hidden; raw input for silver calculation)
 * - GRAM_ALTIN â€¦ GUMUS_GRAM â†’ CALCULATED (visible in API)
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommodityService {

    private static final BigDecimal TROY_OZ_TO_GRAM = new BigDecimal("31.1035");

    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceRepository priceRepository;
    private final CacheService cacheService;
    private final MarketProperties props;
    private final CommoditySymbolRegistry symbolRegistry;

    // â”€â”€ Result record returned by calculateAndSaveDerivedCommodities â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public record DerivedCommodityResult(
            BigDecimal goldUsdOz,
            BigDecimal silverUsdOz,
            BigDecimal usdTry,
            int savedCount,
            List<String> savedSymbols
    ) {}

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Persists Yahoo-fetched commodity quotes.
     * BRENT â†’ YAHOO_FINANCE (public).
     * GOLD_USD / SILVER_USD â†’ INTERNAL (hidden; persisted for use by calculateAndSaveDerivedCommodities).
     *
     * @return goldUsdOz and silverUsdOz extracted from the batch (may be null if not in batch).
     */
    @Transactional
    public BigDecimal[] saveAll(List<StockPriceDto> quotes) {
        BigDecimal goldUsdOz = null;
        BigDecimal silverUsdOz = null;

        if (quotes == null || quotes.isEmpty()) {
            log.warn("CommodityService.saveAll: empty batch received");
            return new BigDecimal[]{null, null};
        }

        log.info("CommodityService.saveAll started. batchSize={}", quotes.size());

        for (StockPriceDto quote : quotes) {
            if (quote == null || isBlank(quote.symbol()) || quote.price() == null) {
                continue;
            }
            String code = quote.symbol().toUpperCase(Locale.ROOT);
            LocalDateTime timestamp = quote.dataTimestamp() != null
                    ? LocalDateTime.ofInstant(quote.dataTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now(ZoneOffset.UTC);

            switch (code) {
                case "BRENT" -> persistPrice(code, SourceName.YAHOO_FINANCE, quote.price(), quote.changePercent(), timestamp);
                case "GOLD_USD" -> {
                    goldUsdOz = quote.price();
                    persistPrice(code, SourceName.INTERNAL, quote.price(), null, timestamp);
                }
                case "SILVER_USD" -> {
                    silverUsdOz = quote.price();
                    persistPrice(code, SourceName.INTERNAL, quote.price(), null, timestamp);
                }
                default -> log.debug("Unknown commodity code from provider, skipping. code={}", code);
            }
        }

        log.info("CommodityService.saveAll completed. goldUsdOz={}, silverUsdOz={}",
                goldUsdOz != null ? goldUsdOz : "not in batch",
                silverUsdOz != null ? silverUsdOz : "not in batch");

        return new BigDecimal[]{goldUsdOz, silverUsdOz};
    }

    /**
     * Calculates Turkish gold/silver derived commodities from explicit inputs.
     * If goldUsdOz or silverUsdOz is null, falls back to the last DB-persisted INTERNAL price.
     * USDTRY is always resolved from FX instruments in DB.
     *
     * @return DerivedCommodityResult with inputs, saved count and saved symbols.
     */
    @Transactional
    public DerivedCommodityResult calculateAndSaveDerivedCommodities(
            BigDecimal goldUsdOz, BigDecimal silverUsdOz) {

        // Fall back to DB-persisted values when caller has no live data
        if (goldUsdOz == null) {
            goldUsdOz = resolveInputFromDb("GOLD_USD");
            if (goldUsdOz != null) {
                log.info("Using DB-persisted GOLD_USD: {}", goldUsdOz);
            } else {
                log.warn("Derived commodity skipped because input missing: goldUsdOz");
            }
        }
        if (silverUsdOz == null) {
            silverUsdOz = resolveInputFromDb("SILVER_USD");
            if (silverUsdOz != null) {
                log.info("Using DB-persisted SILVER_USD: {}", silverUsdOz);
            } else {
                log.warn("Derived commodity skipped because input missing: silverUsdOz");
            }
        }

        BigDecimal usdTry = resolveUsdTry();
        if (usdTry == null) {
            log.warn("Derived commodity skipped because input missing: usdTry");
        }

        log.info("Derived commodity inputs: goldUsdOz={}, silverUsdOz={}, usdTry={}",
                goldUsdOz != null ? goldUsdOz : "MISSING",
                silverUsdOz != null ? silverUsdOz : "MISSING",
                usdTry != null ? usdTry : "MISSING");

        List<String> savedSymbols = new ArrayList<>();

        if (usdTry == null) {
            return new DerivedCommodityResult(goldUsdOz, silverUsdOz, null, 0, savedSymbols);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (goldUsdOz != null) {
            BigDecimal gramAltin = goldUsdOz.multiply(usdTry)
                    .divide(TROY_OZ_TO_GRAM, 4, RoundingMode.HALF_UP);
            BigDecimal ceyrekAltin      = gramAltin.multiply(new BigDecimal("1.75")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal yarimAltin       = ceyrekAltin.multiply(new BigDecimal("2")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal tamAltin         = ceyrekAltin.multiply(new BigDecimal("4")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal cumhuriyetAltini = gramAltin.multiply(new BigDecimal("7.216")).setScale(4, RoundingMode.HALF_UP);

            saveCalculated("GRAM_ALTIN",        gramAltin,        now, savedSymbols);
            saveCalculated("CEYREK_ALTIN",      ceyrekAltin,      now, savedSymbols);
            saveCalculated("YARIM_ALTIN",       yarimAltin,       now, savedSymbols);
            saveCalculated("TAM_ALTIN",         tamAltin,         now, savedSymbols);
            saveCalculated("CUMHURIYET_ALTINI", cumhuriyetAltini, now, savedSymbols);
        }

        if (silverUsdOz != null) {
            BigDecimal gumusGram = silverUsdOz.multiply(usdTry)
                    .divide(TROY_OZ_TO_GRAM, 4, RoundingMode.HALF_UP);
            saveCalculated("GUMUS_GRAM", gumusGram, now, savedSymbols);
        }

        log.info("Derived commodities calculated. count={}, goldUsdOz={}, silverUsdOz={}, usdTry={}",
                savedSymbols.size(), goldUsdOz, silverUsdOz, usdTry);

        return new DerivedCommodityResult(goldUsdOz, silverUsdOz, usdTry, savedSymbols.size(), savedSymbols);
    }

    /**
     * Reads the latest DB-persisted INTERNAL price for a commodity code.
     * Called by admin endpoint and as fallback when Yahoo batch is incomplete.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveInputFromDb(String code) {
        return instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.COMMODITY)
                .flatMap(i -> priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(i))
                .map(MarketPrice::getPriceValue)
                .orElse(null);
    }

    /**
     * Returns all public commodities: YAHOO_FINANCE (BRENT) + CALCULATED (Turkish gold/silver).
     * INTERNAL instruments (GOLD_USD, SILVER_USD raw inputs) are excluded.
     */
    @Transactional(readOnly = true)
    public List<MarketQuoteResponse> getAll() {
        List<MarketInstrument> instruments = new ArrayList<>();
        List<MarketInstrument> yahooInstruments = instrumentRepository.findAllByInstrumentTypeAndSourceName(
                InstrumentType.COMMODITY, SourceName.YAHOO_FINANCE);
        List<MarketInstrument> calculatedInstruments = instrumentRepository.findAllByInstrumentTypeAndSourceName(
                InstrumentType.COMMODITY, SourceName.CALCULATED);
        instruments.addAll(yahooInstruments);
        instruments.addAll(calculatedInstruments);

        log.info("CommodityService.getAll: found yahooCount={}, calculatedCount={}, total={}",
                yahooInstruments.size(), calculatedInstruments.size(), instruments.size());

        List<MarketQuoteResponse> responses = instruments.stream()
                .map(i -> {
                    MarketQuoteResponse r = toResponseFromInstrument(i);
                    if (r == null) {
                        log.warn("CommodityService.getAll: no price for instrument code={} source={}",
                                i.getInstrumentCode(), i.getSourceName());
                    }
                    return r;
                })
                .filter(Objects::nonNull)
                .toList();

        log.info("CommodityService.getAll: returning {} items with prices", responses.size());
        return responses;
    }

    @Transactional(readOnly = true)
    public MarketQuoteResponse getBySymbol(String symbol) {
        String code = symbol.toUpperCase(Locale.ROOT);
        Optional<MarketQuoteResponse> cached = cacheService.get(buildCacheKey(code), MarketQuoteResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        MarketInstrument instrument = instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.COMMODITY)
                .orElseThrow(() -> new InstrumentNotFoundException("Commodity not found: " + code));

        return toResponseFromInstrument(instrument);
    }

    // â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Saves a single CALCULATED commodity price and logs it with instrumentId. */
    private void saveCalculated(String code, BigDecimal price, LocalDateTime timestamp, List<String> savedSymbols) {
        if (price == null || price.signum() <= 0) {
            log.warn("Skipping calculated price save, invalid price. code={}, price={}", code, price);
            return;
        }

        MarketInstrument instrument = instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.COMMODITY)
                .orElseGet(() -> instrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(code)
                        .instrumentName(symbolRegistry.toDisplayName(code))
                        .instrumentType(InstrumentType.COMMODITY)
                        .sourceName(SourceName.CALCULATED)
                        .build()));

        priceRepository.save(MarketPrice.builder()
                .instrument(instrument)
                .priceValue(price)
                .changeRate(null)
                .priceTimestamp(timestamp)
                .sourceName(SourceName.CALCULATED)
                .build());

        log.info("Calculated commodity saved: code={} price={} instrumentId={}", code, price, instrument.getId());
        putCacheSilently(buildCacheKey(code), toResponse(instrument, price, null, timestamp));
        savedSymbols.add(code);
    }

    /** Saves any commodity price (YAHOO_FINANCE or INTERNAL). */
    private void persistPrice(String code, SourceName sourceName, BigDecimal price,
                              BigDecimal changeRate, LocalDateTime timestamp) {
        if (price == null || price.signum() <= 0) {
            log.warn("Skipping price save, invalid price. code={}, price={}", code, price);
            return;
        }

        MarketInstrument instrument = instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.COMMODITY)
                .orElseGet(() -> instrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(code)
                        .instrumentName(symbolRegistry.toDisplayName(code))
                        .instrumentType(InstrumentType.COMMODITY)
                        .sourceName(sourceName)
                        .build()));

        priceRepository.save(MarketPrice.builder()
                .instrument(instrument)
                .priceValue(price)
                .changeRate(changeRate)
                .priceTimestamp(timestamp)
                .sourceName(sourceName)
                .build());

        log.info("Commodity price saved: code={} price={} source={}", code, price, sourceName);
        putCacheSilently(buildCacheKey(code), toResponse(instrument, price, changeRate, timestamp));
    }

    /**
     * Resolves the most recent USD/TRY sell price from FX instruments.
     *
     * <p>Supported code formats: TCMB:USD:SELL, TCMB:USDTRY:SELL, AKBANK:USD:SELL,
     * ZIRAAT:USD:SELL, etc. Any FX instrument whose code contains both "USD" and "SELL"
     * (case-insensitive) and does not contain a conflicting major currency (EUR, GBP â€¦)
     * qualifies. TCMB-sourced candidates are preferred; ties broken by latest price timestamp.
     */
    public BigDecimal resolveUsdTry() {
        try {
            // Fetch all FX instruments whose code contains "USD"
            List<MarketInstrument> usdFxInstruments = instrumentRepository
                    .findAllByInstrumentTypeAndInstrumentCodeContainingIgnoreCase(
                            InstrumentType.FX, "USD");

            // Keep only sell-side USD/TRY â€” exclude cross rates and buy-side
            List<MarketInstrument> candidates = usdFxInstruments.stream()
                    .filter(i -> {
                        String code = i.getInstrumentCode().toUpperCase(Locale.ROOT);
                        return code.contains("SELL")
                                && !code.contains("EUR")
                                && !code.contains("GBP")
                                && !code.contains("JPY")
                                && !code.contains("CHF")
                                && !code.contains("AUD")
                                && !code.contains("CAD");
                    })
                    .toList();

            log.info("resolveUsdTry: candidates found count={}", candidates.size());

            if (candidates.isEmpty()) {
                log.warn("resolveUsdTry: no USD SELL FX instrument in DB " +
                        "(searched FX instruments containing 'USD', total={}, after SELL filter=0)",
                        usdFxInstruments.size());
                return null;
            }

            // Pair each candidate with its latest price row
            record CandidatePrice(MarketInstrument instrument, MarketPrice price) {}

            List<CandidatePrice> withPrices = candidates.stream()
                    .flatMap(i -> priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(i)
                            .map(p -> new CandidatePrice(i, p))
                            .stream())
                    .toList();

            if (withPrices.isEmpty()) {
                log.warn("resolveUsdTry: {} candidate instruments found but none has a price row",
                        candidates.size());
                return null;
            }

            // Prefer TCMB; among equals, pick the most recent timestamp
            CandidatePrice selected = withPrices.stream()
                    .max(Comparator
                            .comparingInt((CandidatePrice cp) ->
                                    cp.instrument().getSourceName() == SourceName.TCMB ? 1 : 0)
                            .thenComparing(cp -> cp.price().getPriceTimestamp()))
                    .orElse(null);

            if (selected == null) {
                return null;
            }

            log.info("resolveUsdTry: selected code={}, source={}, price={}, timestamp={}",
                    selected.instrument().getInstrumentCode(),
                    selected.instrument().getSourceName(),
                    selected.price().getPriceValue(),
                    selected.price().getPriceTimestamp());

            return selected.price().getPriceValue();

        } catch (Exception e) {
            log.warn("resolveUsdTry failed unexpectedly", e);
            return null;
        }
    }

    private MarketQuoteResponse toResponseFromInstrument(MarketInstrument instrument) {
        MarketPrice latest = priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument).orElse(null);
        if (latest == null) {
            return null;
        }
        return toResponse(instrument, latest.getPriceValue(), latest.getChangeRate(), latest.getPriceTimestamp());
    }

    private MarketQuoteResponse toResponse(MarketInstrument instrument, BigDecimal price,
                                           BigDecimal changeRate, LocalDateTime updatedAt) {
        return new MarketQuoteResponse(
                instrument.getInstrumentCode(),
                instrument.getInstrumentName(),
                price,
                changeRate,
                instrument.getSourceName().name(),
                updatedAt,
                InstrumentType.COMMODITY.name()
        );
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getCommodity());
        } catch (Exception e) {
            log.warn("Cache write failed for key={}", key, e);
        }
    }

    private String buildCacheKey(String code) {
        return "commodity:" + code;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}




