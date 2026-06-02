package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.news.dto.response.RelatedInstrumentDto;
import com.emrehalli.financeportal.news.dto.response.RelatedNewsItemDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Conservative relation service that prioritises precision over recall.
 * Returns empty results rather than uncertain matches.
 * Activated when news.relations.mode=conservative (default).
 */
@Service
public class ConservativeNewsRelationService {

    private static final Logger logger = LogManager.getLogger(ConservativeNewsRelationService.class);

    private static final int MAX_INSTRUMENTS = 3;
    private static final int MAX_RELATED_NEWS = 4;
    private static final int RELATED_NEWS_LOOKBACK_DAYS = 7;
    private static final int RECENCY_HOURS = 72;
    private static final int MIN_SCORE_DIRECT = 55;
    private static final int MIN_SCORE_NO_DIRECT = 70;
    private static final double DUPLICATE_TITLE_THRESHOLD = 0.80;

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    // --- Market impact gate ---

    /** Individual tokens that are always strong enough to open the gate. */
    private static final Set<String> STRONG_GATE_TOKENS = Set.of(
            "TCMB", "FED", "ECB", "FOMC",
            "FAIZ", "ENFLASYON", "TAHVIL", "BONO", "GETIRI",
            "KUR", "DOVIZ", "DOLAR", "EURO", "PARITE",
            "BORSA", "BIST", "ENDEKS", "HISSE",
            "ALTIN", "ONS",
            "PETROL", "BRENT", "EMTIA",
            "BANKA", "BANKACILIK", "KREDI", "MEVDUAT",
            "BILANCO", "TEMETTU", "HALKA",
            "KAP"
    );

    /** Export/import signals require one of these to open the gate. */
    private static final Set<String> EXPORT_CONTEXT_TOKENS = Set.of(
            "KUR", "MALIYET", "FINANSMAN", "REKABETCILIK"
    );

    // --- Macro signal groups for instrument inference ---

    private static final Set<String> RATE_FX_TOKENS = Set.of(
            "TCMB", "FED", "ECB", "FOMC", "FAIZ", "ENFLASYON", "TAHVIL", "KUR", "DOVIZ"
    );
    private static final Set<String> DOLAR_TOKENS = Set.of("DOLAR", "USD", "USDTRY");
    private static final Set<String> EURO_TOKENS = Set.of("EURO", "EUR", "EURTRY", "PARITE");
    private static final Set<String> MARKET_TOKENS = Set.of("BORSA", "BIST", "ENDEKS", "XU100", "BIST100");
    private static final Set<String> GOLD_TOKENS = Set.of("ALTIN", "ONS", "GOLD", "XAUUSD");
    private static final Set<String> OIL_TOKENS = Set.of("PETROL", "BRENT", "HAM", "AKARYAKIT");

    private final NewsRepository newsRepository;
    private final MarketQueryService marketQueryService;
    private final FinancialImpactClassifier classifier;

    public ConservativeNewsRelationService(NewsRepository newsRepository,
                                           MarketQueryService marketQueryService,
                                           FinancialImpactClassifier classifier) {
        this.newsRepository = newsRepository;
        this.marketQueryService = marketQueryService;
        this.classifier = classifier;
    }

    // ============================================================
    // Instruments
    // ============================================================

    public List<RelatedInstrumentDto> resolveInstruments(News news) {
        // KAP disclosure: only the official company, nothing else
        if (Boolean.TRUE.equals(news.getIsKapDisclosure()) && hasText(news.getRelatedSymbol())) {
            return resolveKapInstruments(news);
        }

        FinancialImpactResult impact = classifier.classify(
                news.getTitle(), news.getSummary(), null,
                news.getProvider(), null, news.getCategory(), news.getUrl());

        logger.info("conservative.instruments.classify: newsId={}, impactType={}, confidence={}, score={}, matchedSignals={}",
                news.getId(), impact.impactType(), impact.confidence(), impact.score(), impact.matchedSignals());

        if (!impact.marketRelevant()) {
            logger.debug("conservative.instruments.not_market_relevant: newsId={}", news.getId());
            return List.of();
        }

        Set<String> tokens = buildTokens(news);
        Map<String, InstrumentMatch> matched = new LinkedHashMap<>();

        // Direct company matches only when classifier identified a company-level event
        if (impact.impactType() == ImpactType.DIRECT_COMPANY) {
            collectDirectStockMatches(tokens, matched, news.getId());
        }

        // Always preserve explicit text-level FX/commodity/index mentions
        collectDirectMacroTextMatches(tokens, matched, news.getId());

        // Classifier-driven broad instrument mapping (replaces inferred macro heuristics)
        collectInstrumentsByImpactType(impact.impactType(), matched, news.getId());

        List<InstrumentMatch> filtered = matched.values().stream()
                .filter(m -> {
                    if ("STOCK".equalsIgnoreCase(m.instrumentType())
                            && !"DIRECT_COMPANY_MATCH".equalsIgnoreCase(m.matchType())) {
                        logger.debug("conservative.REJECTED_STOCK_NO_DIRECT: newsId={}, symbol={}, matchType={}",
                                news.getId(), m.symbol(), m.matchType());
                        return false;
                    }
                    return true;
                })
                .filter(m -> !"LOW".equalsIgnoreCase(m.confidence()))
                .limit(MAX_INSTRUMENTS)
                .toList();

        if (logger.isDebugEnabled()) {
            filtered.forEach(m -> logger.debug(
                    "conservative.instrument.accepted: newsId={}, symbol={}, matchType={}, confidence={}, reason={}",
                    news.getId(), m.symbol(), m.matchType(), m.confidence(), m.reason()));
        }

        return filtered.stream().map(this::toDto).toList();
    }

    private void collectInstrumentsByImpactType(ImpactType impactType,
                                                 Map<String, InstrumentMatch> matched, Long newsId) {
        switch (impactType) {
            case MONETARY_POLICY -> {
                // No individual bank stocks â€” use XBANK index instead
                matched.putIfAbsent("USDTRY", contextual("USDTRY", "USD/TRY", "FX",
                        "FX_CONTEXT_MATCH", "Para politikasÄ± kararÄ± dÃ¶viz kurunu etkiler"));
                matched.putIfAbsent("EURTRY", contextual("EURTRY", "EUR/TRY", "FX",
                        "FX_CONTEXT_MATCH", "Para politikasÄ± kararÄ± dÃ¶viz kurunu etkiler"));
                matched.putIfAbsent("XU100", contextual("XU100", "BIST 100", "INDEX",
                        "MACRO_CONTEXT_MATCH", "Para politikasÄ± kararÄ± borsa endeksini etkiler"));
                matched.putIfAbsent("XBANK", contextual("XBANK", "BankacÄ±lÄ±k Endeksi", "INDEX",
                        "SECTOR_CONTEXT_MATCH", "Para politikasÄ± bankacÄ±lÄ±k sektÃ¶rÃ¼nÃ¼ doÄŸrudan etkiler"));
            }
            case FISCAL_POLICY -> {
                matched.putIfAbsent("XU100", contextual("XU100", "BIST 100", "INDEX",
                        "MACRO_CONTEXT_MATCH", "Mali politika borsa endeksini etkiler"));
                matched.putIfAbsent("USDTRY", contextual("USDTRY", "USD/TRY", "FX",
                        "FX_CONTEXT_MATCH", "Hazine/bÃ¼tÃ§e geliÅŸmeleri dÃ¶viz kurunu etkiler"));
            }
            case POLITICAL_RISK -> {
                matched.putIfAbsent("XU100", contextual("XU100", "BIST 100", "INDEX",
                        "MACRO_CONTEXT_MATCH", "Siyasi belirsizlik borsa endeksini etkiler"));
                matched.putIfAbsent("USDTRY", contextual("USDTRY", "USD/TRY", "FX",
                        "FX_CONTEXT_MATCH", "Siyasi risk dÃ¶viz kurunu etkiler"));
                matched.putIfAbsent("GOLD", contextual("GOLD", "AltÄ±n", "COMMODITY",
                        "COMMODITY_CONTEXT_MATCH", "Siyasi risk gÃ¼venli liman talebini artÄ±rÄ±r"));
            }
            case GEOPOLITICAL -> {
                matched.putIfAbsent("XU100", contextual("XU100", "BIST 100", "INDEX",
                        "MACRO_CONTEXT_MATCH", "Jeopolitik geliÅŸme borsa endeksini etkiler"));
                matched.putIfAbsent("USDTRY", contextual("USDTRY", "USD/TRY", "FX",
                        "FX_CONTEXT_MATCH", "Jeopolitik risk dÃ¶viz kurunu etkiler"));
                matched.putIfAbsent("GOLD", contextual("GOLD", "AltÄ±n", "COMMODITY",
                        "COMMODITY_CONTEXT_MATCH", "Jeopolitik gerginlik altÄ±n talebini artÄ±rÄ±r"));
                matched.putIfAbsent("BRENT", contextual("BRENT", "Brent Petrol", "COMMODITY",
                        "COMMODITY_CONTEXT_MATCH", "Jeopolitik geliÅŸme petrol fiyatÄ±nÄ± etkiler"));
            }
            case GLOBAL_MARKET -> {
                matched.putIfAbsent("XU100", contextual("XU100", "BIST 100", "INDEX",
                        "MACRO_CONTEXT_MATCH", "KÃ¼resel piyasa geliÅŸmesi endeksi etkiler"));
                matched.putIfAbsent("GOLD", contextual("GOLD", "AltÄ±n", "COMMODITY",
                        "COMMODITY_CONTEXT_MATCH", "KÃ¼resel belirsizlik altÄ±n talebini etkiler"));
                matched.putIfAbsent("BRENT", contextual("BRENT", "Brent Petrol", "COMMODITY",
                        "COMMODITY_CONTEXT_MATCH", "KÃ¼resel piyasa deÄŸiÅŸimi petrolÃ¼ etkiler"));
                matched.putIfAbsent("USDTRY", contextual("USDTRY", "USD/TRY", "FX",
                        "FX_CONTEXT_MATCH", "KÃ¼resel piyasa dÃ¶viz kurunu etkiler"));
            }
            case MACRO -> {
                matched.putIfAbsent("XU100", contextual("XU100", "BIST 100", "INDEX",
                        "MACRO_CONTEXT_MATCH", "Makroekonomik geliÅŸme borsa endeksini etkiler"));
                matched.putIfAbsent("USDTRY", contextual("USDTRY", "USD/TRY", "FX",
                        "FX_CONTEXT_MATCH", "Makroekonomik geliÅŸme dÃ¶viz kurunu etkiler"));
            }
            case REGULATORY -> {
                matched.putIfAbsent("XU100", contextual("XU100", "BIST 100", "INDEX",
                        "SECTOR_CONTEXT_MATCH", "DÃ¼zenleyici kararÄ± piyasayÄ± etkiler"));
            }
            case SECTOR -> {
                matched.putIfAbsent("XBANK", contextual("XBANK", "BankacÄ±lÄ±k Endeksi", "INDEX",
                        "SECTOR_CONTEXT_MATCH", "BankacÄ±lÄ±k sektÃ¶rÃ¼ geliÅŸmesi endeksi etkiler"));
            }
            case DIRECT_COMPANY, NOT_MARKET_RELEVANT -> { /* handled elsewhere */ }
        }
        logger.debug("conservative.instruments.byImpact: newsId={}, impactType={}, added={}",
                newsId, impactType, matched.keySet());
    }

    private InstrumentMatch contextual(String symbol, String name, String instrumentType,
                                        String matchType, String reason) {
        return new InstrumentMatch(symbol, name, instrumentType, matchType, "CONTEXTUAL", reason);
    }

    private List<RelatedInstrumentDto> resolveKapInstruments(News news) {
        String sym = normalizeSymbol(news.getRelatedSymbol());
        NewsService.InstrumentAlias alias = NewsService.BIST_INSTRUMENT_ALIASES.get(sym);
        InstrumentMatch match = alias != null
                ? new InstrumentMatch(alias.symbol(), alias.name(), alias.instrumentType(),
                        "OFFICIAL_KAP_MATCH", "HIGH", "KAP bildirimi doÄŸrudan bu ÅŸirketle iliÅŸkili")
                : new InstrumentMatch(sym, sym, InstrumentType.STOCK.name(),
                        "OFFICIAL_KAP_MATCH", "HIGH", "KAP bildirimi doÄŸrudan bu ÅŸirketle iliÅŸkili");
        logger.debug("conservative.kap.instrument: newsId={}, symbol={}", news.getId(), match.symbol());
        return List.of(toDto(match));
    }

    private boolean isMarketImpactEligible(Set<String> tokens, News news) {
        if (Boolean.TRUE.equals(news.getIsKapDisclosure())) return true;

        // Direct company/ticker mention qualifies immediately
        boolean hasDirectCompany = NewsService.BIST_INSTRUMENT_ALIASES.values().stream()
                .anyMatch(a -> a.keywords().stream().anyMatch(tokens::contains));
        if (hasDirectCompany) return true;

        // Any strong market gate signal (excluding export-only)
        Set<String> gateHits = tokens.stream().filter(STRONG_GATE_TOKENS::contains).collect(Collectors.toSet());
        if (!gateHits.isEmpty()) {
            boolean onlyExport = gateHits.stream().allMatch(t -> "IHRACAT".equals(t) || "ITHALAT".equals(t));
            if (!onlyExport) return true;
        }

        // Export/import + financial context compound rule
        boolean hasExportImport = tokens.contains("IHRACAT") || tokens.contains("ITHALAT");
        boolean hasExportContext = EXPORT_CONTEXT_TOKENS.stream().anyMatch(tokens::contains);
        return hasExportImport && hasExportContext;
    }

    private void collectDirectStockMatches(Set<String> tokens, Map<String, InstrumentMatch> matched, Long newsId) {
        for (NewsService.InstrumentAlias alias : NewsService.BIST_INSTRUMENT_ALIASES.values()) {
            if (!"STOCK".equalsIgnoreCase(alias.instrumentType())) continue;
            if (alias.keywords().stream().anyMatch(tokens::contains)) {
                matched.putIfAbsent(alias.symbol(), new InstrumentMatch(
                        alias.symbol(), alias.name(), alias.instrumentType(),
                        "DIRECT_COMPANY_MATCH", "HIGH", "Haberde ÅŸirket adÄ± geÃ§tiÄŸi iÃ§in iliÅŸkilendirildi"));
                logger.debug("conservative.direct.stock: newsId={}, symbol={}", newsId, alias.symbol());
            }
        }
        // XU100 as direct match (INDEX in BIST aliases)
        NewsService.InstrumentAlias xu100 = NewsService.BIST_INSTRUMENT_ALIASES.get("XU100");
        if (xu100 != null && xu100.keywords().stream().anyMatch(tokens::contains)) {
            matched.putIfAbsent("XU100", new InstrumentMatch(
                    "XU100", "BIST 100", InstrumentType.INDEX.name(),
                    "MACRO_CONTEXT_MATCH", "HIGH", "BIST 100 endeksi haberde doÄŸrudan geÃ§tiÄŸi iÃ§in iliÅŸkilendirildi"));
        }
    }

    private void collectDirectMacroTextMatches(Set<String> tokens, Map<String, InstrumentMatch> matched, Long newsId) {
        // Direct FX mentions â†’ HIGH confidence
        if (containsAny(tokens, DOLAR_TOKENS)) {
            matched.putIfAbsent("USDTRY", new InstrumentMatch("USDTRY", "USD/TRY", InstrumentType.FX.name(),
                    "FX_CONTEXT_MATCH", "HIGH", "Haberde dolar/kur doÄŸrudan geÃ§tiÄŸi iÃ§in iliÅŸkilendirildi"));
        }
        if (containsAny(tokens, EURO_TOKENS)) {
            matched.putIfAbsent("EURTRY", new InstrumentMatch("EURTRY", "EUR/TRY", InstrumentType.FX.name(),
                    "FX_CONTEXT_MATCH", "HIGH", "Haberde euro/parite doÄŸrudan geÃ§tiÄŸi iÃ§in iliÅŸkilendirildi"));
        }
        // Direct gold/oil mentions â†’ HIGH confidence
        if (containsAny(tokens, GOLD_TOKENS)) {
            matched.putIfAbsent("GOLD", new InstrumentMatch("GOLD", "AltÄ±n", InstrumentType.COMMODITY.name(),
                    "COMMODITY_CONTEXT_MATCH", "HIGH", "Haberde altÄ±n doÄŸrudan geÃ§tiÄŸi iÃ§in iliÅŸkilendirildi"));
        }
        if (containsAny(tokens, OIL_TOKENS)) {
            matched.putIfAbsent("BRENT", new InstrumentMatch("BRENT", "Brent Petrol", InstrumentType.COMMODITY.name(),
                    "COMMODITY_CONTEXT_MATCH", "HIGH", "Haberde petrol doÄŸrudan geÃ§tiÄŸi iÃ§in iliÅŸkilendirildi"));
        }
        // Direct market/index mention â†’ HIGH
        if (containsAny(tokens, MARKET_TOKENS)) {
            matched.putIfAbsent("XU100", new InstrumentMatch("XU100", "BIST 100", InstrumentType.INDEX.name(),
                    "MACRO_CONTEXT_MATCH", "HIGH", "Haberde borsa/endeks doÄŸrudan geÃ§tiÄŸi iÃ§in iliÅŸkilendirildi"));
        }
    }

    private void collectInferredMacroMatches(Set<String> tokens, Map<String, InstrumentMatch> matched, Long newsId) {
        // TCMB/FED/ECB/FAIZ/KUR/DOVIZ/ENFLASYON â†’ USDTRY, EURTRY, XU100 (CONTEXTUAL)
        if (containsAny(tokens, RATE_FX_TOKENS)) {
            matched.putIfAbsent("USDTRY", new InstrumentMatch("USDTRY", "USD/TRY", InstrumentType.FX.name(),
                    "FX_CONTEXT_MATCH", "CONTEXTUAL", "TCMB/faiz/kur baÄŸlamÄ± nedeniyle dÃ¶viz kuru etkisi"));
            matched.putIfAbsent("EURTRY", new InstrumentMatch("EURTRY", "EUR/TRY", InstrumentType.FX.name(),
                    "FX_CONTEXT_MATCH", "CONTEXTUAL", "TCMB/faiz/kur baÄŸlamÄ± nedeniyle dÃ¶viz kuru etkisi"));
            matched.putIfAbsent("XU100", new InstrumentMatch("XU100", "BIST 100", InstrumentType.INDEX.name(),
                    "MACRO_CONTEXT_MATCH", "CONTEXTUAL", "Faiz/kur/makro baÄŸlamÄ± nedeniyle piyasa endeksi etkisi"));
        }
        // HISSE â†’ XU100
        if (tokens.contains("HISSE") || tokens.contains("HISSELER")) {
            matched.putIfAbsent("XU100", new InstrumentMatch("XU100", "BIST 100", InstrumentType.INDEX.name(),
                    "MACRO_CONTEXT_MATCH", "CONTEXTUAL", "Hisse senedi baÄŸlamÄ± nedeniyle endeks etkisi"));
        }
        // ALTIN â†’ also add GRAM_ALTIN
        if (containsAny(tokens, GOLD_TOKENS)) {
            matched.putIfAbsent("GRAM_ALTIN", new InstrumentMatch("GRAM_ALTIN", "Gram AltÄ±n",
                    InstrumentType.COMMODITY.name(),
                    "COMMODITY_CONTEXT_MATCH", "CONTEXTUAL", "AltÄ±n baÄŸlamÄ± nedeniyle gram altÄ±n etkisi"));
        }
    }

    private RelatedInstrumentDto toDto(InstrumentMatch m) {
        Optional<MarketQueryService.MarketSnapshot> snapshot;
        try {
            snapshot = marketQueryService.findBySymbol(m.symbol(), parseInstrumentType(m.instrumentType()));
        } catch (Exception e) {
            snapshot = Optional.empty();
        }
        return RelatedInstrumentDto.builder()
                .symbol(m.symbol())
                .name(snapshot.map(MarketQueryService.MarketSnapshot::displayName)
                        .filter(this::hasText).orElse(m.name()))
                .instrumentType(snapshot.map(MarketQueryService.MarketSnapshot::instrumentType)
                        .orElse(m.instrumentType()))
                .lastPrice(snapshot.map(MarketQueryService.MarketSnapshot::price).orElse(null))
                .changePercent(snapshot.map(MarketQueryService.MarketSnapshot::changeRate).orElse(null))
                .relationType(matchTypeToRelationType(m.matchType()))
                .confidence(m.confidence())
                .reason(m.reason())
                .build();
    }

    // ============================================================
    // Related news
    // ============================================================

    public List<RelatedNewsItemDto> resolveRelatedNews(News news) {
        LocalDateTime publishedAfter = effectiveDate(news).minusDays(RELATED_NEWS_LOOKBACK_DAYS);

        List<News> candidates;
        try {
            candidates = hasText(news.getCategory())
                    ? newsRepository.findRecentCandidatesForRelatedNewsByCategory(
                            news.getId(), news.getCategory(), publishedAfter)
                    : newsRepository.findRecentCandidatesForRelatedNews(news.getId(), publishedAfter);
        } catch (Exception e) {
            logger.warn("conservative.relatedNews.fetchFailed: newsId={}, reason={}", news.getId(), e.getMessage());
            return List.of();
        }

        Set<String> srcTokens = buildTokens(news);
        Set<String> srcStocks = extractStockSymbols(srcTokens);
        Set<String> srcMacro = extractMacroInstruments(srcTokens);
        Set<String> srcInstitutions = extractInstitutions(srcTokens);

        return candidates.stream()
                .map(c -> {
                    Set<String> cTokens = buildTokens(c);
                    int score = scoreCandidate(news, srcTokens, srcStocks, srcMacro, srcInstitutions, c, cTokens);
                    return new ScoredCandidate(c, score);
                })
                .filter(sc -> sc.score() > 0)
                .filter(sc -> !isDuplicateTitle(news.getTitle(), sc.candidate().getTitle()))
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed()
                        .thenComparing(sc -> sc.candidate().getPublishedAt(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RELATED_NEWS)
                .map(sc -> RelatedNewsItemDto.builder()
                        .id(sc.candidate().getId())
                        .title(sc.candidate().getTitle())
                        .sourceName(sc.candidate().getSource())
                        .publishedAt(sc.candidate().getPublishedAt())
                        .category(sc.candidate().getCategory())
                        .build())
                .toList();
    }

    private int scoreCandidate(
            News src, Set<String> srcTokens,
            Set<String> srcStocks, Set<String> srcMacro, Set<String> srcInstitutions,
            News cand, Set<String> cTokens) {

        int score = 0;
        boolean hasDirectMatch = false;

        // Same KAP company: strongest signal
        if (Boolean.TRUE.equals(src.getIsKapDisclosure())
                && Boolean.TRUE.equals(cand.getIsKapDisclosure())
                && hasText(src.getRelatedSymbol())
                && src.getRelatedSymbol().equalsIgnoreCase(cand.getRelatedSymbol())) {
            score += 100;
            hasDirectMatch = true;
        }

        // Company/ticker overlap
        Set<String> cStocks = extractStockSymbols(cTokens);
        int stockOverlap = overlapCount(srcStocks, cStocks);
        if (stockOverlap > 0) {
            score += Math.min(70, stockOverlap * 35);
            hasDirectMatch = true;
        }

        // Macro instrument overlap (FX, commodity)
        Set<String> cMacro = extractMacroInstruments(cTokens);
        int macroOverlap = overlapCount(srcMacro, cMacro);
        if (macroOverlap > 0) {
            score += Math.min(50, macroOverlap * 25);
            hasDirectMatch = true;
        }

        // Institution overlap (TCMB, FED, ECB, OPEC)
        Set<String> cInstitutions = extractInstitutions(cTokens);
        int instOverlap = overlapCount(srcInstitutions, cInstitutions);
        if (instOverlap > 0) {
            score += Math.min(35, instOverlap * 35);
            hasDirectMatch = true;
        }

        // Category match (weak signal alone)
        if (hasText(src.getCategory()) && src.getCategory().equalsIgnoreCase(cand.getCategory())) {
            score += 10;
        }

        // Title token overlap (capped to avoid generic words dominating)
        score += Math.min(20, overlapCount(srcTokens, cTokens) * 2);

        // Recency bonus
        if (withinHours(src.getPublishedAt(), cand.getPublishedAt(), RECENCY_HOURS)) {
            score += 10;
        }

        int minScore = hasDirectMatch ? MIN_SCORE_DIRECT : MIN_SCORE_NO_DIRECT;
        if (score < minScore) {
            logger.debug("conservative.relatedNews.rejected: srcId={}, candId={}, score={}, required={}, hasDirectMatch={}",
                    src.getId(), cand.getId(), score, minScore, hasDirectMatch);
            return 0;
        }
        logger.debug("conservative.relatedNews.accepted: srcId={}, candId={}, score={}", src.getId(), cand.getId(), score);
        return score;
    }

    // ============================================================
    // Utilities
    // ============================================================

    private Set<String> buildTokens(News news) {
        return tokenize(String.join(" ",
                news.getTitle() != null ? news.getTitle() : "",
                news.getSummary() != null ? news.getSummary() : ""));
    }

    Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return Arrays.stream(TOKEN_SPLIT.split(normalized))
                .filter(t -> t.length() >= 2)
                .collect(Collectors.toSet());
    }

    private Set<String> extractStockSymbols(Set<String> tokens) {
        Set<String> symbols = new LinkedHashSet<>();
        for (Map.Entry<String, NewsService.InstrumentAlias> e : NewsService.BIST_INSTRUMENT_ALIASES.entrySet()) {
            if ("STOCK".equalsIgnoreCase(e.getValue().instrumentType())
                    && e.getValue().keywords().stream().anyMatch(tokens::contains)) {
                symbols.add(e.getKey());
            }
        }
        return symbols;
    }

    private Set<String> extractMacroInstruments(Set<String> tokens) {
        Set<String> instruments = new LinkedHashSet<>();
        for (Map.Entry<String, NewsService.InstrumentAlias> e : NewsService.MARKET_INSTRUMENT_ALIASES.entrySet()) {
            if (e.getValue().keywords().stream().anyMatch(tokens::contains)) {
                instruments.add(e.getKey());
            }
        }
        return instruments;
    }

    private Set<String> extractInstitutions(Set<String> tokens) {
        Set<String> result = new LinkedHashSet<>();
        if (tokens.contains("TCMB")) result.add("TCMB");
        if (tokens.contains("FED") || tokens.contains("FOMC")) result.add("FED");
        if (tokens.contains("ECB")) result.add("ECB");
        if (tokens.contains("OPEC")) result.add("OPEC");
        if (tokens.contains("BDDK")) result.add("BDDK");
        return result;
    }

    private boolean containsAny(Set<String> tokens, Set<String> candidates) {
        return candidates.stream().anyMatch(tokens::contains);
    }

    private int overlapCount(Set<String> a, Set<String> b) {
        return (int) a.stream().filter(b::contains).count();
    }

    private boolean isDuplicateTitle(String t1, String t2) {
        if (t1 == null || t2 == null) return false;
        Set<String> tok1 = tokenize(t1);
        Set<String> tok2 = tokenize(t2);
        if (tok1.isEmpty() || tok2.isEmpty()) return false;
        int overlap = overlapCount(tok1, tok2);
        return (double) overlap / Math.max(tok1.size(), tok2.size()) >= DUPLICATE_TITLE_THRESHOLD;
    }

    private boolean withinHours(LocalDateTime a, LocalDateTime b, int hours) {
        if (a == null || b == null) return false;
        return Math.abs(ChronoUnit.HOURS.between(a, b)) <= hours;
    }

    private LocalDateTime effectiveDate(News news) {
        return news.getPublishedAt() != null ? news.getPublishedAt() : LocalDateTime.now();
    }

    private String normalizeSymbol(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private InstrumentType parseInstrumentType(String value) {
        if (!hasText(value)) return null;
        try {
            return InstrumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String matchTypeToRelationType(String matchType) {
        return switch (matchType != null ? matchType : "") {
            case "DIRECT_COMPANY_MATCH", "OFFICIAL_KAP_MATCH" -> "DIRECT";
            default -> "THEME";
        };
    }

    private record InstrumentMatch(
            String symbol, String name, String instrumentType,
            String matchType, String confidence, String reason) {}

    private record ScoredCandidate(News candidate, int score) {}
}




