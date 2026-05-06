package com.emrehalli.financeportal.news.provider.investing;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class InvestingRssNewsProvider implements NewsProvider {

    private final InvestingRssNewsClient investingRssNewsClient;
    private final InvestingNewsProperties properties;

    public InvestingRssNewsProvider(InvestingRssNewsClient investingRssNewsClient, InvestingNewsProperties properties) {
        this.investingRssNewsClient = investingRssNewsClient;
        this.properties = properties;
    }

    @Override
    public String getProviderName() {
        return "INVESTING_RSS";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        return investingRssNewsClient.fetchNews();
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
