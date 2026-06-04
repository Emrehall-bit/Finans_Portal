package com.emrehalli.financeportal.ai.features.news;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keyword-based news category detector.
 * Categories are evaluated in enum declaration order â€” more specific categories
 * (TCMB, FED) are placed before the broader ones (INTEREST_RATE) to avoid
 * mis-classification on overlapping keywords.
 */
@Component
public class NewsCategoryDetector {

    private static final Map<NewsCategory, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry(NewsCategory.TCMB,               List.of("tcmb", "merkez bankasÄ±", "merkez bank", "para politikasÄ±")),
            Map.entry(NewsCategory.FED,                List.of("federal reserve", "fomc", "powell", "yellen", "fed faiz")),
            Map.entry(NewsCategory.INFLATION,          List.of("enflasyon", "tÃ¼fe", "Ã¼fe", " cpi", " ppi", "inflation", "fiyat artÄ±ÅŸ")),
            Map.entry(NewsCategory.INTEREST_RATE,      List.of("faiz kararÄ±", "faiz oranÄ±", "interest rate", "baz puan", "basis point")),
            Map.entry(NewsCategory.OIL_ENERGY,         List.of("petrol", "brent", "doÄŸalgaz", "doÄŸal gaz", "enerji fiyat", " opec", "lng")),
            Map.entry(NewsCategory.DEFENSE,            List.of("savunma sanayii", "savunma sanayi", "silah sistem", "askeri teknoloji", "savunma harcamasi", "defense")),
            Map.entry(NewsCategory.AVIATION,           List.of("havacÄ±lÄ±k", "havayolu", "thyao", "thy hava", "pegasus", "aviation", "airline")),
            Map.entry(NewsCategory.BANKING,            List.of("bankacÄ±lÄ±k sektÃ¶rÃ¼", "banka kÃ¢r", "banka zarar", "kredi bÃ¼yÃ¼me", "mevduat faiz")),
            Map.entry(NewsCategory.CRYPTO,             List.of("kripto", "bitcoin", "ethereum", " btc", " eth", "blockchain", "crypto", "coin fiyat")),
            Map.entry(NewsCategory.REGULATION,         List.of("regÃ¼lasyon", "dÃ¼zenleme kararÄ±", " spk ", " bddk ", "yasal dÃ¼zenleme", "regulation")),
            Map.entry(NewsCategory.EARNINGS,           List.of("bilanÃ§o", "kÃ¢r aÃ§Ä±kladÄ±", "zarar aÃ§Ä±kladÄ±", "net kÃ¢r", "earnings", "revenue")),
            Map.entry(NewsCategory.DIVIDEND,           List.of("temettÃ¼", "kar payÄ±", "dividend")),
            Map.entry(NewsCategory.MERGER_ACQUISITION, List.of("birleÅŸme", "satÄ±n alma anlaÅŸmasÄ±", "merger", "acquisition", "devralmak")),
            Map.entry(NewsCategory.INVESTMENT,         List.of("tahvil ihraÃ§", "bono ihraÃ§", "yatÄ±rÄ±m fonu", "investment fund"))
    );

    public NewsCategory detect(String title, String summary, String existingCategory) {
        NewsCategory mappedExistingCategory = mapExistingCategory(existingCategory);
        if (mappedExistingCategory != null) {
            return mappedExistingCategory;
        }
        String combined = buildSearchText(title, summary);
        for (NewsCategory category : NewsCategory.values()) {
            if (category == NewsCategory.GENERAL) continue;
            List<String> keywords = KEYWORDS.getOrDefault(category, List.of());
            for (String kw : keywords) {
                if (combined.contains(kw)) {
                    return category;
                }
            }
        }
        return NewsCategory.GENERAL;
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

    private String buildSearchText(String title, String summary) {
        String t = title   != null ? title.toLowerCase(Locale.ROOT)   : "";
        String s = summary != null ? summary.toLowerCase(Locale.ROOT) : "";
        return t + " " + s;
    }
}




