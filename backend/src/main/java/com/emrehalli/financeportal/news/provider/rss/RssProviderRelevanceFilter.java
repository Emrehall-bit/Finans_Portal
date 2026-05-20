package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.enums.NewsProviderType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RssProviderRelevanceFilter {

    private static final List<String> FINANCE_KEYWORDS = List.of(
            "market", "markets", "economy", "economic", "finance", "financial", "fed", "ecb", "central bank",
            "inflation", "interest rate", "rates", "stocks", "shares", "equity", "earnings", "ipo", "bank",
            "bond", "oil", "gas", "energy", "currency", "forex", "trade", "tariff", "recession", "growth",
            "treasury", "macro", "business", "company", "companies", "crypto", "bitcoin", "ethereum", "ai",
            "artificial intelligence", "semiconductor", "nvidia", "sanctions", "geopolitical", "yield"
    );
    private static final List<String> NON_FINANCE_KEYWORDS = List.of(
            "sports", "sport", "soccer", "football", "basketball", "tennis", "golf", "lifestyle", "travel",
            "fashion", "celebrity", "entertainment", "movie", "music", "tv", "television", "recipe", "health"
    );

    public boolean isRelevant(NewsProviderType providerType, String title, String summary, String category, String link) {
        if (providerType != NewsProviderType.CNBC_RSS
                && providerType != NewsProviderType.WORLD_NEWS_API) {
            return true;
        }

        String combined = normalize(String.join(" ", safe(title), safe(summary), safe(category), safe(link)));
        boolean excluded = NON_FINANCE_KEYWORDS.stream().anyMatch(combined::contains);
        if (excluded) {
            return false;
        }

        return FINANCE_KEYWORDS.stream().anyMatch(combined::contains);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
