package com.emrehalli.financeportal.news.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
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
    private static final List<String> FX_STRONG_CONTEXT_KEYWORDS = List.of(
            "doviz pozisyonu", "doviz pozisyon", "net doviz pozisyonu", "net doviz pozisyon",
            "doviz pozisyon acigi", "doviz acigi", "doviz kuru",
            "kur riski", "kur etkisi", "kur farki",
            "dolar tl", "euro tl", "usd try", "eur try", "usdtry", "eurtry",
            "doviz rezervi", "rezerv para",
            "yabanci para pozisyonu", "yabanci para pozisyon",
            "euro dolar", "eur usd", "usd eur"
    );
    private static final List<String> BANKING_EARLY_SIGNALS = List.of(
            "bankacilik", "banka", "bankasi", "bankalari", "bankaciligi",
            "garanti bankasi", "is bankasi", "halkbank", "ziraat bankasi",
            "akbank", "yapi kredi", "vakifbank", "finansbank",
            "mevduat", "mevduati", "kredi", "krediye", "kredide", "kredisi",
            "loan", "deposit", "net interest margin", "net faiz marji",
            "bankacilik hisseleri", "banka hisseleri", "takipteki kredi"
    );
    private static final List<String> GOLD_COMMODITY_EARLY_SIGNALS = List.of(
            "altin", "gold", "ons", "gram altin", "gumus", "silver",
            "emtia", "commodity", "bakir", "copper",
            "petrol", "oil", "brent", "opec", "dogalgaz", "natural gas", "lng", "rafineri",
            "enerji", "energy"
    );
    // Core: anywhere in combined (Turkish + specific financial terms)
    private static final List<String> INTEREST_BONDS_CORE_STRONG_SIGNALS = List.of(
            "faiz", "tahvil", "bono", "tcmb", "ecb", "fomc", "para politikasi", "baz puan",
            "politika faizi", "faiz karari", "tahvil getirisi", "yield", "getiri", "merkez bankasi"
    );
    // Title-only: count as strong ONLY if they appear in title (not summary/content boilerplate)
    private static final List<String> INTEREST_BONDS_TITLE_ONLY_SIGNALS = List.of(
            "rate hike", "rate cut", "rate cuts", "rate hikes"
    );
    // Weak: never produce IB alone; blocked when no strong signal present
    private static final Set<String> INTEREST_BONDS_WEAK_KEYWORDS = Set.of(
            "interest", "fed", "interest rate", "central bank", "powell"
    );
    private static final LinkedHashMap<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_HINT_MAPPING = createCategoryHintMapping();

    static {
        CATEGORY_KEYWORDS.put(INTEREST_BONDS, List.of(
                "tcmb", "merkez bankasi", "central bank", "faiz", "interest rate", "interest rates",
                "interest", "tahvil", "bono", "yield", "getiri", "fed", "ecb", "fomc", "powell",
                "para politikasi", "baz puan", "politika faizi", "faiz karari", "tahvil getirisi",
                "rate hike", "rate cut", "rate cuts", "rate hikes"
        ));
        CATEGORY_KEYWORDS.put(FX, List.of(
                "dolar tl", "euro tl", "usd try", "eur try", "usdtry", "eurtry",
                "doviz pozisyonu", "doviz pozisyon", "net doviz pozisyonu", "net doviz pozisyon",
                "doviz pozisyon acigi", "doviz acigi", "doviz kuru",
                "kur riski", "kur etkisi", "kur farki",
                "doviz rezervi", "rezerv para",
                "yabanci para pozisyonu", "yabanci para pozisyon",
                "euro dolar", "eur usd", "usd eur"
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
                "bakir", "copper", "metal",
                "petrol", "oil", "brent", "opec", "dogalgaz", "natural gas", "lng", "rafineri",
                "arz endisesi", "supply disruption", "petrol fiyati", "petrol fiyatlari",
                "enerji fiyati", "enerji fiyatlari", "enerji emtiasi"
        ));
        CATEGORY_KEYWORDS.put(CRYPTO, List.of(
                "kripto", "crypto", "bitcoin", "ethereum", "btc", "eth", "stablecoin", "blockchain"
        ));
        CATEGORY_KEYWORDS.put(GLOBAL_MARKETS, List.of(
                "global markets",
                "kuresel piyasalar", "kuresel piyasalarda", "kuresel piyasalara", "kuresel piyasalarin",
                "global piyasa", "global piyasalarda",
                "wall street", "nasdaq", "dow", "dow jones",
                "sp500", "s p 500", "msci",
                "asian markets", "avrupa borsalari", "risk appetite",
                "dax", "ftse", "nikkei",
                "kuresel enerji piyasasi", "petrol piyasasi"
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
                "ihracat", "export", "ithalat", "import", "cari acik", "macro", "resesyon", "recession",
                "yenilenebilir enerji", "enerji bakanligi", "enerji verimliligi"
        ));
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
        if (hasPoliticalContent && !hasFinancialContext) {
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

        if (hasFinancialContext) {
            return ClassificationResult.accepted(GENERAL_ECONOMY);
        }

        return ClassificationResult.accepted(GENERAL_ECONOMY);
    }

    public Set<String> resolveFilterCategories(String requestedCategory) {
        if (!hasText(requestedCategory)) {
            return Set.of();
        }
        String uppercaseRequested = requestedCategory.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return Set.of(uppercaseRequested);
    }

    public boolean isPrimaryCategory(String category) {
        return hasText(category) && PRIMARY_CATEGORIES.contains(category.trim().toUpperCase(Locale.ROOT));
    }

    private int scoreCategory(
            String combined,
            String normalizedTitle,
            String normalizedSummary,
            String normalizedHint,
            String category,
            List<String> keywords
    ) {
        if (FX.equals(category) && !containsAny(combined, FX_STRONG_CONTEXT_KEYWORDS)) {
            return 0;
        }
        if (BANKING.equals(category)
                && !isEarlySignalPresent(normalizedTitle, normalizedSummary, BANKING_EARLY_SIGNALS)) {
            return 0;
        }
        if (GOLD_COMMODITY.equals(category)
                && !isEarlySignalPresent(normalizedTitle, normalizedSummary, GOLD_COMMODITY_EARLY_SIGNALS)) {
            return 0;
        }
        boolean ibWeakBlocked = INTEREST_BONDS.equals(category)
                && !isIbStrongSignalPresent(normalizedTitle, normalizedSummary, combined);
        int score = 0;
        for (String keyword : keywords) {
            if (ibWeakBlocked && INTEREST_BONDS_WEAK_KEYWORDS.contains(keyword)) {
                continue;
            }
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

    private boolean isEarlySignalPresent(String normalizedTitle, String normalizedSummary, List<String> signals) {
        String earlyWindow = normalizedSummary.length() > 400
                ? normalizedSummary.substring(0, 400)
                : normalizedSummary;
        for (String signal : signals) {
            if (containsKeyword(normalizedTitle, signal) || containsKeyword(earlyWindow, signal)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIbStrongSignalPresent(String normalizedTitle, String normalizedSummary, String combined) {
        if (containsAny(combined, INTEREST_BONDS_CORE_STRONG_SIGNALS)) {
            return true;
        }
        if (containsAny(normalizedTitle, INTEREST_BONDS_TITLE_ONLY_SIGNALS)) {
            return true;
        }
        // "interest rates": strong only in title or first 400 chars of summary
        String earlyWindow = normalizedSummary.length() > 400
                ? normalizedSummary.substring(0, 400)
                : normalizedSummary;
        return containsKeyword(normalizedTitle, "interest rates")
                || containsKeyword(earlyWindow, "interest rates");
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
                Map.entry("energy", GOLD_COMMODITY),
                Map.entry("oil", GOLD_COMMODITY),
                Map.entry("petrol", GOLD_COMMODITY),
                Map.entry("company", COMPANY)
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
