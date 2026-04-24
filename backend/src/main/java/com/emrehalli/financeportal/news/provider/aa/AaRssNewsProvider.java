package com.emrehalli.financeportal.news.provider.aa;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.aa.client.AaRssNewsClient;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AaRssNewsProvider implements NewsProvider {

    private final AaRssNewsClient aaRssNewsClient;
    private final AaNewsProperties properties;

    public AaRssNewsProvider(AaRssNewsClient aaRssNewsClient, AaNewsProperties properties) {
        this.aaRssNewsClient = aaRssNewsClient;
        this.properties = properties;
    }

    @Override
    public String getProviderName() {
        return "AA_RSS";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        return aaRssNewsClient.fetchEconomyNews();
    }

    @Override
    public List<NewsItemDto> fetchCompanyNews(String symbol) {
        List<NewsItemDto> allNews = fetchLatestNews();
        if (symbol == null || symbol.isBlank()) {
            return allNews;
        }

        String normalized = symbol.trim().toLowerCase(Locale.ROOT);
        return allNews.stream()
                .filter(item -> containsIgnoreCase(item.getTitle(), normalized)
                        || containsIgnoreCase(item.getSummary(), normalized))
                .toList();
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }
}
