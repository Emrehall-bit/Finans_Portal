package com.emrehalli.financeportal.news.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final LinkedHashMap<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> LEGACY_FILTER_MAPPING = createLegacyFilterMapping();
    private static final Map<String, String> CATEGORY_HINT_MAPPING = createCategoryHintMapping();
    private static final Map<String, List<String>> FILTER_CATEGORY_RUNTIME_KEYWORDS;

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
            return ClassificationResult.accepted(primaryCategory);
        }

        String hintCategory = CATEGORY_HINT_MAPPING.get(normalizedHint);
        if (hintCategory != null) {
            return ClassificationResult.accepted(hintCategory);
        }

        if (hasFinancialContext || hasGeopoliticalContent) {
            boolean geopoliticsQualifies = hasGeopoliticalContent && hasFinancialContext;
            String primaryCategory = geopoliticsQualifies ? GEOPOLITICS : GENERAL_ECONOMY;
            return ClassificationResult.accepted(primaryCategory);
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

    public record ClassificationResult(String category, boolean rejected, String rejectReason) {
        public static ClassificationResult accepted(String category) {
            return new ClassificationResult(category, false, null);
        }

        public static ClassificationResult rejected(String rejectReason) {
            return new ClassificationResult(null, true, rejectReason);
        }
    }
}




