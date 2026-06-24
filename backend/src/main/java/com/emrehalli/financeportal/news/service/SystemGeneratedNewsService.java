package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.generator.PriceChangeContext;
import com.emrehalli.financeportal.news.generator.SystemNewsScenario;
import com.emrehalli.financeportal.news.generator.SystemNewsTextGenerator;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemGeneratedNewsService {

    static final String PROVIDER = "SYSTEM_GENERATED";
    private static final String SOURCE = "Sistem";
    private static final String LANGUAGE = "tr";
    private static final String QUALITY_STATUS = "SUMMARY_ONLY";

    private static final Map<InstrumentType, BigDecimal> THRESHOLDS = new EnumMap<>(InstrumentType.class);
    private static final Map<InstrumentType, Duration> COOLDOWNS = new EnumMap<>(InstrumentType.class);
    private static final Map<InstrumentType, String> REGION_SCOPES = new EnumMap<>(InstrumentType.class);
    private static final Map<InstrumentType, String> CATEGORIES = new EnumMap<>(InstrumentType.class);

    static {
        THRESHOLDS.put(InstrumentType.CRYPTO, new BigDecimal("15"));
        THRESHOLDS.put(InstrumentType.STOCK, new BigDecimal("8"));
        THRESHOLDS.put(InstrumentType.FX, new BigDecimal("2"));
        THRESHOLDS.put(InstrumentType.COMMODITY, new BigDecimal("3"));
        THRESHOLDS.put(InstrumentType.FUND, new BigDecimal("5"));

        COOLDOWNS.put(InstrumentType.CRYPTO, Duration.ofHours(4));
        COOLDOWNS.put(InstrumentType.STOCK, Duration.ofHours(6));
        COOLDOWNS.put(InstrumentType.FX, Duration.ofHours(12));
        COOLDOWNS.put(InstrumentType.COMMODITY, Duration.ofHours(12));
        COOLDOWNS.put(InstrumentType.FUND, Duration.ofHours(12));

        REGION_SCOPES.put(InstrumentType.CRYPTO, "GLOBAL");
        REGION_SCOPES.put(InstrumentType.STOCK, "LOCAL");
        REGION_SCOPES.put(InstrumentType.FX, "GLOBAL");
        REGION_SCOPES.put(InstrumentType.COMMODITY, "GLOBAL");
        REGION_SCOPES.put(InstrumentType.FUND, "LOCAL");

        CATEGORIES.put(InstrumentType.CRYPTO, "KRİPTO");
        CATEGORIES.put(InstrumentType.STOCK, "HİSSE");
        CATEGORIES.put(InstrumentType.FX, "DÖVİZ");
        CATEGORIES.put(InstrumentType.COMMODITY, "EMTİA");
        CATEGORIES.put(InstrumentType.FUND, "FON");
    }

    private final MarketPriceRepository marketPriceRepository;
    private final NewsRepository newsRepository;
    private final SystemNewsTextGenerator textGenerator;

    @Transactional
    public int checkAndGenerateForType(InstrumentType type) {
        BigDecimal threshold = THRESHOLDS.get(type);
        if (threshold == null) {
            return 0;
        }

        // Look back 2 hours — ensures we catch the freshest prices from recent scheduler runs.
        LocalDateTime since = LocalDateTime.now().minusHours(2);
        List<MarketPrice> candidates = marketPriceRepository
                .findLatestSignificantByInstrumentType(type.name(), threshold, since);

        if (candidates.isEmpty()) {
            return 0;
        }

        int generated = 0;
        for (MarketPrice mp : candidates) {
            try {
                if (generateIfEligible(mp, type, threshold)) {
                    generated++;
                }
            } catch (Exception ex) {
                log.warn("System news generation failed for symbol={} type={}",
                        mp.getInstrument().getInstrumentCode(), type, ex);
            }
        }

        log.info("System news check complete. type={} candidates={} generated={}", type, candidates.size(), generated);
        return generated;
    }

    private boolean generateIfEligible(MarketPrice mp, InstrumentType type, BigDecimal threshold) {
        String rawCode = mp.getInstrument().getInstrumentCode();
        String symbol = normalizeSymbolForNews(rawCode, type);
        BigDecimal changeRate = mp.getChangeRate();
        if (changeRate == null) {
            return false;
        }

        if (!isCooldownExpired(symbol, type)) {
            return false;
        }

        String displayName = resolveDisplayName(mp, type, symbol);
        SystemNewsScenario scenario = SystemNewsScenario.from(changeRate, threshold);
        PriceChangeContext ctx = new PriceChangeContext(symbol, displayName, type, changeRate, scenario);

        News news = buildNews(ctx);
        newsRepository.save(news);
        log.info("System news generated. symbol={} scenario={} change={}%", symbol, scenario, changeRate);
        return true;
    }

    // Returns true when cooldown has expired (or no prior news exists) → eligible to generate.
    private boolean isCooldownExpired(String symbol, InstrumentType type) {
        Duration cooldown = COOLDOWNS.get(type);
        Optional<News> last = newsRepository
                .findTopByProviderAndRelatedSymbolOrderByPublishedAtDesc(PROVIDER, symbol);
        if (last.isEmpty()) {
            return true;
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(cooldown);
        return last.get().getPublishedAt().isBefore(cutoff);
    }

    private String resolveDisplayName(MarketPrice mp, InstrumentType type, String normalizedSymbol) {
        String instrumentName = mp.getInstrument().getInstrumentName();
        String rawCode = mp.getInstrument().getInstrumentCode();
        // For STOCK the instrumentName stored in DB is the company name — use it directly.
        if (type == InstrumentType.STOCK
                && instrumentName != null
                && !instrumentName.isBlank()
                && !instrumentName.equals(rawCode)) {
            return instrumentName;
        }
        return textGenerator.resolveDisplayName(normalizedSymbol, type);
    }

    // FX: "TCMB:USD:BUY" → "USD",  Crypto: "BTCUSDT" → "BTC",  others unchanged.
    private String normalizeSymbolForNews(String instrumentCode, InstrumentType type) {
        if (type == InstrumentType.FX && instrumentCode.contains(":")) {
            String[] parts = instrumentCode.split(":");
            return parts.length >= 2 ? parts[1].toUpperCase() : instrumentCode;
        }
        if (type == InstrumentType.CRYPTO) {
            for (String suffix : List.of("USDT", "BUSD", "USDC", "TRY")) {
                if (instrumentCode.toUpperCase().endsWith(suffix) && instrumentCode.length() > suffix.length()) {
                    return instrumentCode.substring(0, instrumentCode.length() - suffix.length()).toUpperCase();
                }
            }
        }
        return instrumentCode;
    }

    private News buildNews(PriceChangeContext ctx) {
        String title = textGenerator.generateTitle(ctx);
        String summary = textGenerator.generateSummary(ctx);
        String direction = ctx.scenario().isRise() ? "UP" : "DOWN";
        String externalId = PROVIDER + ":" + ctx.symbol() + ":" + direction + ":" + System.currentTimeMillis();
        String url = "internal://system-alert/" + ctx.symbol().toLowerCase();

        return News.builder()
                .externalId(externalId)
                .title(title)
                .summary(summary)
                .source(SOURCE)
                .provider(PROVIDER)
                .language(LANGUAGE)
                .regionScope(REGION_SCOPES.getOrDefault(ctx.instrumentType(), "GLOBAL"))
                .category(CATEGORIES.get(ctx.instrumentType()))
                .relatedSymbol(ctx.symbol())
                .url(url)
                .publishedAt(LocalDateTime.now())
                .qualityStatus(QUALITY_STATUS)
                .isKapDisclosure(false)
                .importanceScore(resolveImportanceScore(ctx.scenario()))
                .build();
    }

    private int resolveImportanceScore(SystemNewsScenario scenario) {
        return switch (scenario) {
            case EXTREME_RISE, EXTREME_FALL -> 75;
            case STRONG_RISE, SHARP_FALL -> 50;
        };
    }
}
