package com.emrehalli.financeportal.news.provider.reuters;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ReutersRssNewsProvider implements NewsProvider {

    private final ReutersRssNewsClient client;
    private final ReutersNewsProperties properties;

    public ReutersRssNewsProvider(ReutersRssNewsClient client, ReutersNewsProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String getProviderName() {
        return "REUTERS_RSS";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        return client.fetchNews();
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
