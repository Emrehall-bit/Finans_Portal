package com.emrehalli.financeportal.news.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class NewsCategoryClassifier {

    public static final String GENERAL_ECONOMY = "GENERAL_ECONOMY";
    public static final String GLOBAL_MARKETS = "GLOBAL_MARKETS";
    public static final String FX = "FX";
    public static final String STOCKS = "STOCKS";
    public static final String INTEREST_BONDS = "INTEREST_BONDS";
    public static final String GOLD_COMMODITY = "GOLD_COMMODITY";
    public static final String BANKING = "BANKING";
    public static final String CRYPTO = "CRYPTO";
    public static final String ENERGY = "ENERGY";
    public static final String GEOPOLITICS = "GEOPOLITICS";
    public static final String COMPANY = "COMPANY";

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Set<String> PRIMARY_CATEGORIES = Set.of(
            GENERAL_ECONOMY,
            GLOBAL_MARKETS,
            FX,
            STOCKS,
            INTEREST_BONDS,
            GOLD_COMMODITY,
            BANKING,
            CRYPTO,
            ENERGY,
            GEOPOLITICS,
            COMPANY
    );
    private static final List<String> FINANCIAL_CONTEXT_KEYWORDS = List.of(
            "faiz", "interest", "bond", "tahvil", "bono", "yield", "kur", "doviz", "fx", "forex",
            "enflasyon", "inflation", "vergi", "butce", "merkez bankasi", "central bank",
            "tcmb", "fed", "ecb", "borsa", "bist", "hisse", "stock", "equity", "piyasa", "market",
            "petrol", "oil", "enerji", "energy", "arz", "supply", "kripto", "crypto", "bitcoin",
            "bankacilik", "banka", "bank", "emtia", "commodity", "altin", "gold", "savas", "war",
            "yaptirim", "sanction", "global market", "kuresel piyasa"
    );
    private static final List<String> NON_FINANCE_REJECT_KEYWORDS = List.of(
            "futbol", "football", "soccer", "basketbol", "basketball", "tenis", "tennis", "golf",
            "magazin", "celebrity", "entertainment", "movie", "music", "lifestyle", "travel",
            "fashion", "recipe", "health", "wellness", "transfer haberi", "transfer news"
    );
    private static final List<String> POLITICAL_KEYWORDS = List.of(
            "siyasi parti", "parti", "secim", "election", "campaign", "milletvekili", "parlamento",
            "parliament", "kongre", "congress", "cumhurbaskani", "president", "bakan", "ministry",
            "muhalefet", "iktidar", "belediye", "governor", "miting", "oylama"
    );
    private static final List<String> GEOPOLITICAL_KEYWORDS = List.of(
            "iran", "israil", "ukrayna", "rusya", "russia", "orta dogu", "middle east", "gerilim",
            "tension", "savas", "war", "ateskes", "ceasefire", "nato", "yaptirim", "sanction",
            "trade war", "tariff", "abd cin", "us china"
    );
    private static final List<String> MARKET_IMPACT_KEYWORDS = List.of(
            "piyasa", "market", "borsa", "endeks", "index", "risk", "fiyatlama", "pricing", "yatirimci", "investor"
    );
    private static final List<String> FX_CONTEXT_KEYWORDS = List.of(
            "dolar tl", "dolar/tl", "euro tl", "euro/tl", "usd try", "eur try", "parite", "kur",
            "doviz", "forex", "fx", "sterlin", "yen", "currency pair"
    );
    private static final List<String> FX_CURRENCY_KEYWORDS = List.of(
            "dolar", "usd", "euro", "eur", "sterlin", "gbp", "yen", "jpy", "tl", "try"
    );
    private static final List<String> GOLD_CONTEXT_KEYWORDS = List.of(
            "altin", "gold", "ons", "gram altin", "gumus", "silver", "emtia", "commodity", "guvenli liman", "safe haven"
    );
    private static final List<String> ENERGY_CONTEXT_KEYWORDS = List.of(
            "petrol", "oil", "brent", "opec", "dogalgaz", "natural gas", "lng", "enerji", "energy", "arz", "supply"
    );
    private static final List<String> STOCK_CONTEXT_KEYWORDS = List.of(
            "borsa", "bist", "hisse", "hisseleri", "pay", "stock", "stocks", "shares", "equity", "endeks", "index", "halka acik", "halka arz"
    );
    private static final List<String> COMPANY_ONLY_KEYWORDS = List.of(
            "sirket", "company", "ceo", "fabrika", "plant", "anlasma", "agreement", "yatirim plani", "investment plan"
    );
    private static final LinkedHashMap<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> LEGACY_FILTER_MAPPING = createLegacyFilterMapping();
    private static final Map<String, String> CATEGORY_HINT_MAPPING = createCategoryHintMapping();
    private static final Map<String, List<String>> FILTER_CATEGORY_RUNTIME_KEYWORDS;
    private static final Map<String, Set<String>> PRIMARY_CATEGORY_FILTER_TAGS = createPrimaryCategoryFilterTags();

    static {
        CATEGORY_KEYWORDS.put(INTEREST_BONDS, List.of(
                "tcmb", "merkez bankasi", "central bank", "faiz", "interest rate", "interest", "tahvil",
                "bono", "yield", "getiri", "fed", "ecb", "fomc", "powell", "para politikasi", "baz puan"
        ));
        CATEGORY_KEYWORDS.put(FX, List.of(
                "dolar tl", "euro tl", "usd try", "eur try", "usdtry", "eurtry",
                "doviz", "dovize", "dovizde", "dovizi", "dovizin",
                "forex", "fx",
                "kur", "kuru", "kurda", "kuruna", "kurdan", "kurunda",
                "parite", "sterlin", "yen",
                "doviz kuru", "exchange rate"
        ));
        CATEGORY_KEYWORDS.put(BANKING, List.of(
                "bankacilik", "banka", "bankasi", "bankalari", "bankaciligi",
                "garanti bankasi", "is bankasi", "halkbank", "ziraat bankasi",
                "akbank", "yapi kredi", "vakifbank", "finansbank",
                "mevduat", "mevduati", "kredi", "krediye", "kredide", "kredisi",
                "loan", "deposit",
                "net interest margin", "net faiz marji",
                "bankacilik hisseleri", "banka hisseleri", "takipteki kredi"
        ));
        CATEGORY_KEYWORDS.put(STOCKS, List.of(
                "borsa istanbul", "borsa", "bist", "hisse", "hisseleri", "hisse senedi", "stock",
                "stocks", "shares", "equity", "endeks", "index", "halka arz", "ipo"
        ));
        CATEGORY_KEYWORDS.put(GOLD_COMMODITY, List.of(
                "altin", "gold", "ons", "gram altin", "gumus", "silver", "emtia", "commodity",
                "bakir", "copper", "metal"
        ));
        CATEGORY_KEYWORDS.put(ENERGY, List.of(
                "petrol", "oil", "brent", "opec", "dogalgaz", "natural gas", "lng", "enerji",
                "energy", "arz endisesi", "supply disruption", "rafineri"
        ));
        CATEGORY_KEYWORDS.put(CRYPTO, List.of(
                "kripto", "crypto", "bitcoin", "ethereum", "btc", "eth", "stablecoin", "blockchain"
        ));
        CATEGORY_KEYWORDS.put(GEOPOLITICS, List.of(
                "orta dogu", "middle east", "gerilim", "tension",
                "savas", "war", "ateskes", "ceasefire", "nato",
                "yaptirim", "sanction", "trade war", "tariff"
        ));
        CATEGORY_KEYWORDS.put(GLOBAL_MARKETS, List.of(
                "global markets",
                "kuresel piyasalar", "kuresel piyasalarda", "kuresel piyasalara", "kuresel piyasalarin",
                "global piyasa", "global piyasalarda",
                "wall street", "nasdaq", "dow", "dow jones",
                "sp500", "s p 500", "msci",
                "asian markets", "avrupa borsalari", "risk appetite",
                "dax", "ftse", "nikkei"
        ));
        CATEGORY_KEYWORDS.put(COMPANY, List.of(
                "company", "ceo", "earnings", "revenue", "guidance",
                "merger", "acquisition", "partnership", "agreement",
                "temettu", "bilanco", "net kar", "kar aciklamasi",
                "birlesme", "devralma", "geri alim", "sermaye artirimi"
        ));
        CATEGORY_KEYWORDS.put(GENERAL_ECONOMY, List.of(
                "ekonomi", "economy", "enflasyon", "inflation", "buyume", "growth", "issizlik",
                "unemployment", "gsyh", "gdp", "cpi", "ppi", "vergi", "tax", "butce", "budget",
                "ihracat", "export", "ithalat", "import", "cari acik", "macro", "resesyon", "recession"
        ));
        FILTER_CATEGORY_RUNTIME_KEYWORDS = createFilterCategoryRuntimeKeywords();
    }

    public ClassificationResult classify(String title, String summary, String contentPreview, String sourceCategoryHint) {
        String normalizedTitle = normalize(title);
        String normalizedSummary = normalize(summary);
        String normalizedPreview = normalize(contentPreview);
        String normalizedHint = normalize(sourceCategoryHint);
        String combined = String.join(" ", normalizedTitle, normalizedSummary, normalizedPreview).trim();

        boolean hasFinancialContext = containsAny(combined, FINANCIAL_CONTEXT_KEYWORDS);
        if (containsAny(combined, NON_FINANCE_REJECT_KEYWORDS) && !hasFinancialContext) {
            return ClassificationResult.rejected("REJECT_NON_FINANCE_TOPIC");
        }

        boolean hasPoliticalContent = containsAny(combined, POLITICAL_KEYWORDS);
        boolean hasGeopoliticalContent = containsAny(combined, GEOPOLITICAL_KEYWORDS);
        if (hasPoliticalContent && !hasFinancialContext && !hasGeopoliticalContent) {
            return ClassificationResult.rejected("REJECT_NON_FINANCE_POLITICS");
        }

        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            int score = scoreCategory(combined, normalizedTitle, normalizedSummary, normalizedHint, entry.getKey(), entry.getValue());
            if (score > 0) {
                scores.put(entry.getKey(), score);
            }
        }

        if (!scores.isEmpty()) {
            String primaryCategory = selectHighestScore(scores);
            return ClassificationResult.accepted(primaryCategory, deriveTags(primaryCategory, combined, scores.keySet()));
        }

        String hintCategory = CATEGORY_HINT_MAPPING.get(normalizedHint);
        if (hintCategory != null) {
            return ClassificationResult.accepted(hintCategory, deriveTags(hintCategory, combined, Set.of(hintCategory)));
        }

        if (hasFinancialContext || hasGeopoliticalContent) {
            boolean geopoliticsQualifies = hasGeopoliticalContent && hasFinancialContext;
            String primaryCategory = geopoliticsQualifies ? GEOPOLITICS : GENERAL_ECONOMY;
            return ClassificationResult.accepted(primaryCategory, deriveTags(primaryCategory, combined, Set.of(primaryCategory)));
        }

        return ClassificationResult.rejected("REJECT_CATEGORY_UNCLASSIFIED");
    }

    public Set<String> resolveFilterCategories(String requestedCategory) {
        if (!hasText(requestedCategory)) {
            return Set.of();
        }
        String normalized = normalize(requestedCategory).replace(' ', '_');
        String uppercaseRequested = requestedCategory.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add(uppercaseRequested);
        if (PRIMARY_CATEGORIES.contains(uppercaseRequested)) {
            categories.add(uppercaseRequested);
        }
        categories.addAll(LEGACY_FILTER_MAPPING.getOrDefault(normalized, Set.of()));
        return categories;
    }

    public boolean isPrimaryCategory(String category) {
        return hasText(category) && PRIMARY_CATEGORIES.contains(category.trim().toUpperCase(Locale.ROOT));
    }

    public Set<String> resolveFilterRuntimeKeywords(String requestedCategory) {
        if (!hasText(requestedCategory)) {
            return Set.of();
        }
        String normalized = normalize(requestedCategory).replace(' ', '_');
        Set<String> categories = resolveFilterCategories(requestedCategory);
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String category : categories) {
            keywords.addAll(FILTER_CATEGORY_RUNTIME_KEYWORDS.getOrDefault(category, List.of()));
        }
        keywords.addAll(FILTER_CATEGORY_RUNTIME_KEYWORDS.getOrDefault(normalized.toUpperCase(Locale.ROOT), List.of()));
        return keywords;
    }

    public Set<String> resolveCategoryAndTagMatches(String requestedCategory) {
        LinkedHashSet<String> matches = new LinkedHashSet<>(resolveFilterCategories(requestedCategory));
        String normalized = hasText(requestedCategory)
                ? requestedCategory.trim().toUpperCase(Locale.ROOT).replace(' ', '_')
                : "";
        if (PRIMARY_CATEGORIES.contains(normalized)) {
            matches.add(normalized);
        }
        return matches;
    }

    private int scoreCategory(
            String combined,
            String normalizedTitle,
            String normalizedSummary,
            String normalizedHint,
            String category,
            List<String> keywords
    ) {
        int score = 0;
        for (String keyword : keywords) {
            if (containsKeyword(normalizedTitle, keyword)) {
                score += 4;
            }
            if (containsKeyword(normalizedSummary, keyword)) {
                score += 2;
            }
            if (containsKeyword(combined, keyword)) {
                score += 1;
            }
        }
        String mappedHint = CATEGORY_HINT_MAPPING.get(normalizedHint);
        if (category.equals(mappedHint)) {
            score += 1;
        }
        return score;
    }

    private String selectHighestScore(LinkedHashMap<String, Integer> scores) {
        String bestCategory = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestCategory = entry.getKey();
                bestScore = entry.getValue();
            }
        }
        return bestCategory != null ? bestCategory : GENERAL_ECONOMY;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replace('I', 'i')
                .replace('\u0130', 'i')
                .replace('\u0131', 'i')
                .replace('\u015e', 's')
                .replace('\u015f', 's')
                .replace('\u011e', 'g')
                .replace('\u011f', 'g')
                .replace('\u00dc', 'u')
                .replace('\u00fc', 'u')
                .replace('\u00d6', 'o')
                .replace('\u00f6', 'o')
                .replace('\u00c7', 'c')
                .replace('\u00e7', 'c');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized;
    }

    private TagResolution deriveTags(String primaryCategory, String combined, Set<String> matchedCategories) {
        LinkedHashMap<String, String> tagReasons = new LinkedHashMap<>();
        addTag(tagReasons, primaryCategory, "primary-category");
        for (String defaultTag : PRIMARY_CATEGORY_FILTER_TAGS.getOrDefault(primaryCategory, Set.of())) {
            addTag(tagReasons, defaultTag, "primary-default");
        }

        boolean strongFxContext = hasStrongFxContext(combined, matchedCategories, primaryCategory);
        boolean strongGoldContext = hasStrongGoldContext(combined, matchedCategories, primaryCategory);
        boolean strongEnergyContext = hasStrongEnergyContext(combined, matchedCategories, primaryCategory);
        boolean strongStockContext = hasStrongStockContext(combined, matchedCategories, primaryCategory);
        boolean strongGlobalMarketContext = hasStrongGlobalMarketsContext(combined, matchedCategories, primaryCategory, strongFxContext, strongGoldContext, strongEnergyContext, strongStockContext);

        if (strongFxContext) {
            addTag(tagReasons, FX, "currency-context");
        }
        if (strongGoldContext) {
            addTag(tagReasons, GOLD_COMMODITY, "gold-commodity-context");
        }
        if (strongEnergyContext) {
            addTag(tagReasons, ENERGY, "energy-supply-context");
        }
        if (strongStockContext) {
            addTag(tagReasons, STOCKS, "equity-market-context");
        }
        if (matchedCategories.contains(BANKING) || containsAny(combined, CATEGORY_KEYWORDS.getOrDefault(BANKING, List.of()))) {
            addTag(tagReasons, BANKING, "banking-context");
        }
        if (matchedCategories.contains(INTEREST_BONDS) || containsAny(combined, CATEGORY_KEYWORDS.getOrDefault(INTEREST_BONDS, List.of()))) {
            addTag(tagReasons, INTEREST_BONDS, "rates-bonds-context");
        }
        if (strongGlobalMarketContext) {
            addTag(tagReasons, GLOBAL_MARKETS, "cross-market-impact");
        }
        if (matchedCategories.contains(CRYPTO) || containsAny(combined, CATEGORY_KEYWORDS.getOrDefault(CRYPTO, List.of()))) {
            addTag(tagReasons, CRYPTO, "crypto-context");
        }
        if (tagReasons.isEmpty()) {
            addTag(tagReasons, GENERAL_ECONOMY, "fallback");
        }
        return new TagResolution(new LinkedHashSet<>(tagReasons.keySet()), tagReasons);
    }

    private boolean hasStrongFxContext(String combined, Set<String> matchedCategories, String primaryCategory) {
        if (FX.equals(primaryCategory) || matchedCategories.contains(FX)) {
            return true;
        }
        int explicitFxHits = countMatches(combined, FX_CONTEXT_KEYWORDS);
        int currencyHits = countMatches(combined, FX_CURRENCY_KEYWORDS);
        boolean ratesDrivenFxContext = currencyHits >= 1
                && containsAny(combined, List.of("faiz", "interest", "tahvil", "bond", "fed", "tcmb", "merkez bankasi"))
                && containsAny(combined, List.of("piyasa", "piyasalar", "market", "markets", "kur", "doviz", "parite", "pricing", "fiyatlama"));
        return explicitFxHits >= 1
                || (currencyHits >= 2 && containsAny(combined, List.of("kur", "parite", "doviz", "tl", "try", "forex", "fx")))
                || ratesDrivenFxContext;
    }

    private boolean hasStrongGoldContext(String combined, Set<String> matchedCategories, String primaryCategory) {
        if (GOLD_COMMODITY.equals(primaryCategory) || matchedCategories.contains(GOLD_COMMODITY)) {
            return true;
        }
        int goldHits = countMatches(combined, GOLD_CONTEXT_KEYWORDS);
        boolean ratesAndCurrencyContext = containsAny(combined, List.of("faiz", "interest", "tahvil", "bond"))
                && countMatches(combined, FX_CURRENCY_KEYWORDS) >= 1
                && containsAny(combined, List.of("altin", "gold", "ons", "emtia", "commodity"));
        boolean geopoliticsSafeHavenContext = containsAny(combined, GEOPOLITICAL_KEYWORDS)
                && containsAny(combined, List.of("guvenli liman", "safe haven", "altin", "gold", "ons"));
        return goldHits >= 2 || ratesAndCurrencyContext || geopoliticsSafeHavenContext;
    }

    private boolean hasStrongEnergyContext(String combined, Set<String> matchedCategories, String primaryCategory) {
        if (ENERGY.equals(primaryCategory) || matchedCategories.contains(ENERGY)) {
            return true;
        }
        int energyHits = countMatches(combined, ENERGY_CONTEXT_KEYWORDS);
        boolean geopoliticsEnergyContext = containsAny(combined, GEOPOLITICAL_KEYWORDS)
                && containsAny(combined, List.of("petrol", "oil", "brent", "enerji", "energy", "arz", "supply"));
        return energyHits >= 2 || geopoliticsEnergyContext;
    }

    private boolean hasStrongStockContext(String combined, Set<String> matchedCategories, String primaryCategory) {
        if (STOCKS.equals(primaryCategory) || BANKING.equals(primaryCategory) || matchedCategories.contains(STOCKS)) {
            return true;
        }
        int stockHits = countMatches(combined, STOCK_CONTEXT_KEYWORDS);
        boolean companyOnly = countMatches(combined, COMPANY_ONLY_KEYWORDS) > 0;
        return stockHits >= 2 || (stockHits >= 1 && !companyOnly && containsAny(combined, List.of("borsa", "bist", "hisse", "endeks", "halka acik")));
    }

    private boolean hasStrongGlobalMarketsContext(
            String combined,
            Set<String> matchedCategories,
            String primaryCategory,
            boolean strongFxContext,
            boolean strongGoldContext,
            boolean strongEnergyContext,
            boolean strongStockContext
    ) {
        if (GLOBAL_MARKETS.equals(primaryCategory) || GEOPOLITICS.equals(primaryCategory) || matchedCategories.contains(GLOBAL_MARKETS)) {
            return true;
        }
        int crossAssetSignals = 0;
        crossAssetSignals += strongFxContext ? 1 : 0;
        crossAssetSignals += strongGoldContext ? 1 : 0;
        crossAssetSignals += strongEnergyContext ? 1 : 0;
        crossAssetSignals += strongStockContext ? 1 : 0;
        crossAssetSignals += matchedCategories.contains(INTEREST_BONDS) || INTEREST_BONDS.equals(primaryCategory) ? 1 : 0;
        boolean marketLanguage = containsAny(combined, MARKET_IMPACT_KEYWORDS) || containsAny(combined, CATEGORY_KEYWORDS.getOrDefault(GLOBAL_MARKETS, List.of()));
        boolean geopoliticalMarketImpact = containsAny(combined, GEOPOLITICAL_KEYWORDS) && marketLanguage;
        return geopoliticalMarketImpact || (marketLanguage && crossAssetSignals >= 2);
    }

    private int countMatches(String text, List<String> keywords) {
        int matches = 0;
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                matches++;
            }
        }
        return matches;
    }

    private boolean containsKeyword(String normalizedText, String keyword) {
        if (!hasText(normalizedText) || !hasText(keyword)) {
            return false;
        }
        String normalizedKeyword = normalize(keyword);
        if (!hasText(normalizedKeyword)) {
            return false;
        }
        String paddedText = " " + normalizedText + " ";
        String paddedKeyword = " " + normalizedKeyword + " ";
        return paddedText.contains(paddedKeyword);
    }

    private void addTag(Map<String, String> tagReasons, String tag, String reason) {
        if (hasText(tag) && hasText(reason)) {
            tagReasons.putIfAbsent(tag, reason);
        }
    }

    private static Map<String, Set<String>> createLegacyFilterMapping() {
        return Map.ofEntries(
                Map.entry("economy", Set.of(GENERAL_ECONOMY, "ECONOMY", "GENERAL")),
                Map.entry("general", Set.of(GENERAL_ECONOMY, "GENERAL", "ECONOMY")),
                Map.entry("general_economy", Set.of(GENERAL_ECONOMY, "GENERAL", "ECONOMY")),
                Map.entry("business", Set.of(GENERAL_ECONOMY, COMPANY, "BUSINESS")),
                Map.entry("top_news", Set.of(
                        GENERAL_ECONOMY, GLOBAL_MARKETS, FX, STOCKS, INTEREST_BONDS, GOLD_COMMODITY,
                        BANKING, CRYPTO, ENERGY, GEOPOLITICS, COMPANY, "TOP_NEWS"
                )),
                Map.entry("top news", Set.of(
                        GENERAL_ECONOMY, GLOBAL_MARKETS, FX, STOCKS, INTEREST_BONDS, GOLD_COMMODITY,
                        BANKING, CRYPTO, ENERGY, GEOPOLITICS, COMPANY, "TOP_NEWS"
                )),
                Map.entry("markets", Set.of(GLOBAL_MARKETS, STOCKS, FX, INTEREST_BONDS, GOLD_COMMODITY, ENERGY, BANKING, "MARKETS")),
                Map.entry("global_markets", Set.of(GLOBAL_MARKETS, "MARKETS")),
                Map.entry("fx", Set.of(FX, "FOREX", "CURRENCY", "DOVIZ")),
                Map.entry("forex", Set.of(FX, "FOREX", "CURRENCY", "DOVIZ")),
                Map.entry("currency", Set.of(FX, "FOREX", "CURRENCY", "DOVIZ")),
                Map.entry("doviz", Set.of(FX, "FOREX", "CURRENCY", "DOVIZ")),
                Map.entry("stocks", Set.of(STOCKS, "STOCK", "SHARES", "EQUITY")),
                Map.entry("stock", Set.of(STOCKS, "STOCK", "SHARES", "EQUITY")),
                Map.entry("banking", Set.of(BANKING, "BANK")),
                Map.entry("interest_bonds", Set.of(INTEREST_BONDS, "INTEREST_RATE", "BOND", "TAHVIL", "FAIZ")),
                Map.entry("bond", Set.of(INTEREST_BONDS, "INTEREST_RATE", "BOND", "TAHVIL", "FAIZ")),
                Map.entry("faiz", Set.of(INTEREST_BONDS, "INTEREST_RATE", "BOND", "TAHVIL", "FAIZ")),
                Map.entry("gold_commodity", Set.of(GOLD_COMMODITY, "GOLD", "COMMODITY", "EMTIA", "ALTIN")),
                Map.entry("commodity", Set.of(GOLD_COMMODITY, "GOLD", "COMMODITY", "EMTIA", "ALTIN")),
                Map.entry("altin", Set.of(GOLD_COMMODITY, "GOLD", "COMMODITY", "EMTIA", "ALTIN")),
                Map.entry("crypto", Set.of(CRYPTO, "CRYPTOCURRENCY")),
                Map.entry("energy", Set.of(ENERGY, "OIL", "PETROL")),
                Map.entry("geopolitics", Set.of(GEOPOLITICS, "POLITICS")),
                Map.entry("company", Set.of(COMPANY, "BUSINESS"))
        );
    }

    private static Map<String, String> createCategoryHintMapping() {
        return Map.ofEntries(
                Map.entry("economy", GENERAL_ECONOMY),
                Map.entry("general", GENERAL_ECONOMY),
                Map.entry("business", GENERAL_ECONOMY),
                Map.entry("top_news", GLOBAL_MARKETS),
                Map.entry("top news", GLOBAL_MARKETS),
                Map.entry("markets", GLOBAL_MARKETS),
                Map.entry("fx", FX),
                Map.entry("forex", FX),
                Map.entry("currency", FX),
                Map.entry("doviz", FX),
                Map.entry("stocks", STOCKS),
                Map.entry("stock", STOCKS),
                Map.entry("equity", STOCKS),
                Map.entry("banking", BANKING),
                Map.entry("bank", BANKING),
                Map.entry("interest_rate", INTEREST_BONDS),
                Map.entry("interest rate", INTEREST_BONDS),
                Map.entry("bond", INTEREST_BONDS),
                Map.entry("faiz", INTEREST_BONDS),
                Map.entry("gold", GOLD_COMMODITY),
                Map.entry("commodity", GOLD_COMMODITY),
                Map.entry("emtia", GOLD_COMMODITY),
                Map.entry("altin", GOLD_COMMODITY),
                Map.entry("crypto", CRYPTO),
                Map.entry("energy", ENERGY),
                Map.entry("oil", ENERGY),
                Map.entry("petrol", ENERGY),
                Map.entry("geopolitics", GEOPOLITICS),
                Map.entry("politics", GEOPOLITICS),
                Map.entry("company", COMPANY)
        );
    }

    private static Map<String, List<String>> createFilterCategoryRuntimeKeywords() {
        return Map.ofEntries(
                Map.entry(GENERAL_ECONOMY, CATEGORY_KEYWORDS.get(GENERAL_ECONOMY)),
                Map.entry(GLOBAL_MARKETS, List.of("kuresel piyasa", "global market", "wall street", "nasdaq", "dow", "msci", "risk appetite", "piyasa etkisi", "market impact")),
                Map.entry(FX, List.of("dolar tl", "dolar/tl", "euro tl", "euro/tl", "usd try", "eur try", "parite", "doviz kuru", "fx", "forex")),
                Map.entry(STOCKS, List.of("borsa istanbul", "bist", "hisse", "hisseleri", "hisse senedi", "stock", "equity", "shares", "endeks", "halka acik")),
                Map.entry(INTEREST_BONDS, CATEGORY_KEYWORDS.get(INTEREST_BONDS)),
                Map.entry(GOLD_COMMODITY, List.of("altin", "gold", "emtia", "commodity", "gumus", "silver", "petrol", "oil", "brent")),
                Map.entry(BANKING, CATEGORY_KEYWORDS.get(BANKING)),
                Map.entry(CRYPTO, CATEGORY_KEYWORDS.get(CRYPTO)),
                Map.entry("ECONOMY", CATEGORY_KEYWORDS.get(GENERAL_ECONOMY)),
                Map.entry("GENERAL", CATEGORY_KEYWORDS.get(GENERAL_ECONOMY)),
                Map.entry("BUSINESS", CATEGORY_KEYWORDS.get(GENERAL_ECONOMY)),
                Map.entry("TOP_NEWS", List.of("global market", "kuresel piyasa", "market impact", "risk appetite")),
                Map.entry("MARKETS", List.of("global market", "kuresel piyasa", "market impact", "wall street", "nasdaq")),
                Map.entry("FOREX", List.of("dolar tl", "dolar/tl", "euro tl", "euro/tl", "parite", "doviz kuru", "forex", "fx")),
                Map.entry("CURRENCY", List.of("dolar tl", "euro tl", "parite", "doviz kuru", "forex", "fx")),
                Map.entry("DOVIZ", List.of("dolar tl", "euro tl", "parite", "doviz kuru", "forex", "fx")),
                Map.entry("STOCK", List.of("stock", "hisse", "borsa istanbul", "equity", "endeks")),
                Map.entry("SHARES", List.of("shares", "hisseleri", "hisse senedi", "endeks")),
                Map.entry("EQUITY", List.of("equity", "stock", "hisse senedi", "endeks")),
                Map.entry("BANK", CATEGORY_KEYWORDS.get(BANKING)),
                Map.entry("INTEREST_RATE", CATEGORY_KEYWORDS.get(INTEREST_BONDS)),
                Map.entry("BOND", CATEGORY_KEYWORDS.get(INTEREST_BONDS)),
                Map.entry("TAHVIL", CATEGORY_KEYWORDS.get(INTEREST_BONDS)),
                Map.entry("FAIZ", CATEGORY_KEYWORDS.get(INTEREST_BONDS)),
                Map.entry("GOLD", CATEGORY_KEYWORDS.get(GOLD_COMMODITY)),
                Map.entry("COMMODITY", CATEGORY_KEYWORDS.get(GOLD_COMMODITY)),
                Map.entry("EMTIA", CATEGORY_KEYWORDS.get(GOLD_COMMODITY)),
                Map.entry("ALTIN", CATEGORY_KEYWORDS.get(GOLD_COMMODITY)),
                Map.entry("ENERGY", CATEGORY_KEYWORDS.get(ENERGY)),
                Map.entry("OIL", CATEGORY_KEYWORDS.get(ENERGY)),
                Map.entry("PETROL", CATEGORY_KEYWORDS.get(ENERGY)),
                Map.entry("GEOPOLITICS", CATEGORY_KEYWORDS.get(GEOPOLITICS)),
                Map.entry("POLITICS", CATEGORY_KEYWORDS.get(GEOPOLITICS)),
                Map.entry("COMPANY", CATEGORY_KEYWORDS.get(COMPANY)),
                Map.entry("CRYPTOCURRENCY", CATEGORY_KEYWORDS.get(CRYPTO))
        );
    }

    private static Map<String, Set<String>> createPrimaryCategoryFilterTags() {
        return Map.ofEntries(
                Map.entry(GENERAL_ECONOMY, Set.of(GENERAL_ECONOMY)),
                Map.entry(GLOBAL_MARKETS, Set.of(GLOBAL_MARKETS)),
                Map.entry(FX, Set.of(FX)),
                Map.entry(STOCKS, Set.of(STOCKS)),
                Map.entry(INTEREST_BONDS, Set.of(INTEREST_BONDS)),
                Map.entry(GOLD_COMMODITY, Set.of(GOLD_COMMODITY)),
                Map.entry(BANKING, Set.of(BANKING)),
                Map.entry(CRYPTO, Set.of(CRYPTO)),
                Map.entry(ENERGY, Set.of()),
                Map.entry(GEOPOLITICS, Set.of()),
                Map.entry(COMPANY, Set.of())
        );
    }

    public record ClassificationResult(String category, Set<String> tags, Map<String, String> tagReasons, boolean rejected, String rejectReason) {
        public static ClassificationResult accepted(String category, TagResolution resolution) {
            return new ClassificationResult(
                    category,
                    resolution == null || resolution.tags() == null ? Set.of() : resolution.tags().stream().filter(Objects::nonNull).collect(LinkedHashSet::new, Set::add, Set::addAll),
                    resolution == null ? Map.of() : Map.copyOf(resolution.tagReasons()),
                    false,
                    null
            );
        }

        public static ClassificationResult rejected(String rejectReason) {
            return new ClassificationResult(null, Set.of(), Map.of(), true, rejectReason);
        }
    }

    private record TagResolution(Set<String> tags, Map<String, String> tagReasons) {
    }
}




