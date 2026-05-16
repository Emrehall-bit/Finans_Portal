package com.emrehalli.financeportal.ai.news;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keyword-based news category detector.
 * Categories are evaluated in enum declaration order — more specific categories
 * (TCMB, FED) are placed before the broader ones (INTEREST_RATE) to avoid
 * mis-classification on overlapping keywords.
 */
@Component
public class NewsCategoryDetector {

    private static final Map<NewsCategory, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry(NewsCategory.TCMB,               List.of("tcmb", "merkez bankası", "merkez bank", "para politikası")),
            Map.entry(NewsCategory.FED,                List.of("federal reserve", "fomc", "powell", "yellen", "fed faiz")),
            Map.entry(NewsCategory.INFLATION,          List.of("enflasyon", "tüfe", "üfe", " cpi", " ppi", "inflation", "fiyat artış")),
            Map.entry(NewsCategory.INTEREST_RATE,      List.of("faiz kararı", "faiz oranı", "interest rate", "baz puan", "basis point")),
            Map.entry(NewsCategory.OIL_ENERGY,         List.of("petrol", "brent", "doğalgaz", "doğal gaz", "enerji fiyat", " opec", "lng")),
            Map.entry(NewsCategory.DEFENSE,            List.of("savunma sanayii", "savunma sanayi", "askeri", "silah sistem", "defense")),
            Map.entry(NewsCategory.AVIATION,           List.of("havacılık", "havayolu", "thyao", "thy hava", "pegasus", "aviation", "airline")),
            Map.entry(NewsCategory.BANKING,            List.of("bankacılık sektörü", "banka kâr", "banka zarar", "kredi büyüme", "mevduat faiz")),
            Map.entry(NewsCategory.CRYPTO,             List.of("kripto", "bitcoin", "ethereum", " btc", " eth", "blockchain", "crypto", "coin fiyat")),
            Map.entry(NewsCategory.REGULATION,         List.of("regülasyon", "düzenleme kararı", " spk ", " bddk ", "yasal düzenleme", "regulation")),
            Map.entry(NewsCategory.EARNINGS,           List.of("bilanço", "kâr açıkladı", "zarar açıkladı", "net kâr", "earnings", "revenue")),
            Map.entry(NewsCategory.DIVIDEND,           List.of("temettü", "kar payı", "dividend")),
            Map.entry(NewsCategory.MERGER_ACQUISITION, List.of("birleşme", "satın alma anlaşması", "merger", "acquisition", "devralmak")),
            Map.entry(NewsCategory.INVESTMENT,         List.of("tahvil ihraç", "bono ihraç", "yatırım fonu", "investment fund"))
    );

    public NewsCategory detect(String title, String summary, String existingCategory) {
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

    private String buildSearchText(String title, String summary) {
        String t = title   != null ? title.toLowerCase(Locale.ROOT)   : "";
        String s = summary != null ? summary.toLowerCase(Locale.ROOT) : "";
        return t + " " + s;
    }
}
