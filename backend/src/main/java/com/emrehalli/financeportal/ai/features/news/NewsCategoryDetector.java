package com.emrehalli.financeportal.ai.features.news;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keyword-based detector for News Impact AI. App-level news category is only a
 * weak fallback because feed categorization can be noisy.
 */
@Component
public class NewsCategoryDetector {

    public record DetectionResult(
            NewsCategory category,
            boolean titleMatched,
            boolean summaryMatched,
            boolean categoryOnly
    ) {
        public boolean hasContentEvidence() {
            return titleMatched || summaryMatched;
        }
    }

    private static final Map<NewsCategory, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry(NewsCategory.TCMB, List.of("tcmb", "merkez bankası", "merkez bank", "para politikası")),
            Map.entry(NewsCategory.FED, List.of("federal reserve", "fomc", "powell", "yellen", "fed faiz")),
            Map.entry(NewsCategory.INFLATION, List.of("enflasyon", "tüfe", "üfe", " cpi", " ppi", "inflation", "fiyat artış")),
            Map.entry(NewsCategory.INTEREST_RATE, List.of("faiz kararı", "faiz oranı", "interest rate", "baz puan", "basis point")),
            Map.entry(NewsCategory.OIL_ENERGY, List.of("petrol", "brent", "doğalgaz", "doğal gaz", "enerji fiyat", " opec", "lng")),
            Map.entry(NewsCategory.DEFENSE, List.of("savunma sanayii", "savunma sanayi", "silah sistem", "askeri teknoloji", "savunma harcaması", "defense")),
            Map.entry(NewsCategory.AVIATION, List.of("havacılık", "havayolu", "thyao", "thy hava", "pegasus", "aviation", "airline")),
            Map.entry(NewsCategory.BANKING, List.of("bankacılık sektörü", "bankacılık", "banka kâr", "banka kar", "banka zarar", "kredi büyüme", "mevduat faiz")),
            Map.entry(NewsCategory.CRYPTO, List.of("kripto", "bitcoin", "ethereum", " btc", " eth", "blockchain", "crypto", "coin fiyat")),
            Map.entry(NewsCategory.REGULATION, List.of("regülasyon", "düzenleme kararı", " spk ", " bddk ", "yasal düzenleme", "regulation")),
            Map.entry(NewsCategory.EARNINGS, List.of("bilanço", "kâr açıkladı", "kar açıkladı", "zarar açıkladı", "net kâr", "net kar", "earnings", "revenue")),
            Map.entry(NewsCategory.DIVIDEND, List.of("temettü", "kar payı", "dividend")),
            Map.entry(NewsCategory.MERGER_ACQUISITION, List.of("birleşme", "satın alma anlaşması", "merger", "acquisition", "devralmak")),
            Map.entry(NewsCategory.INVESTMENT, List.of("tahvil ihraç", "bono ihraç", "yatırım fonu", "investment fund"))
    );

    public NewsCategory detect(String title, String summary, String existingCategory) {
        return detectWithEvidence(title, summary, existingCategory).category();
    }

    public DetectionResult detectWithEvidence(String title, String summary, String existingCategory) {
        String titleText = lower(title);
        String summaryText = lower(summary);

        NewsCategory titleCategory = matchCategory(titleText);
        if (titleCategory != null) {
            return new DetectionResult(titleCategory, true, false, false);
        }

        NewsCategory summaryCategory = matchCategory(summaryText);
        if (summaryCategory != null) {
            return new DetectionResult(summaryCategory, false, true, false);
        }

        NewsCategory mappedExistingCategory = mapExistingCategory(existingCategory);
        if (mappedExistingCategory != null) {
            return new DetectionResult(mappedExistingCategory, false, false, true);
        }

        return new DetectionResult(NewsCategory.GENERAL, false, false, false);
    }

    private NewsCategory matchCategory(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (NewsCategory category : NewsCategory.values()) {
            if (category == NewsCategory.GENERAL) {
                continue;
            }
            List<String> keywords = KEYWORDS.getOrDefault(category, List.of());
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return category;
                }
            }
        }
        return null;
    }

    private NewsCategory mapExistingCategory(String existingCategory) {
        if (existingCategory == null || existingCategory.isBlank()) {
            return null;
        }
        String normalized = existingCategory.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INTEREST_BONDS", "FX" -> NewsCategory.INTEREST_RATE;
            case "ENERGY", "GOLD_COMMODITY", "GLOBAL_MARKETS" -> NewsCategory.OIL_ENERGY;
            case "BANKING" -> NewsCategory.BANKING;
            case "CRYPTO" -> NewsCategory.CRYPTO;
            case "COMPANY", "STOCKS" -> NewsCategory.EARNINGS;
            case "GEOPOLITICS", "GENERAL_ECONOMY" -> NewsCategory.GENERAL;
            default -> null;
        };
    }

    private String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }
}