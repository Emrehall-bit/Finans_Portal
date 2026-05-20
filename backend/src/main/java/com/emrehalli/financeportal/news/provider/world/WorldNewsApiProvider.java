package com.emrehalli.financeportal.news.provider.world;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnosticsAware;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class WorldNewsApiProvider implements NewsProvider, ProviderSyncDiagnosticsAware {

    private final WorldNewsApiClient client;

    public WorldNewsApiProvider(WorldNewsApiClient client) {
        this.client = client;
    }

    @Override
    public String getProviderName() {
        return "WORLD_NEWS_API";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        return client.fetchLatestNews();
    }

    @Override
    public List<NewsItemDto> fetchLatestNews(int limit) {
        return client.fetchLatestNews(limit);
    }

    @Override
    public List<NewsItemDto> fetchCompanyNews(String symbol) {
        List<NewsItemDto> allNews = fetchLatestNews();
        if (symbol == null || symbol.isBlank()) {
            return allNews;
        }
        String normalized = symbol.trim().toLowerCase(Locale.ROOT);
        return allNews.stream()
                .filter(item -> containsIgnoreCase(item.getTitle(), normalized) || containsIgnoreCase(item.getSummary(), normalized))
                .toList();
    }

    @Override
    public ProviderSyncDiagnostics getLastDiagnostics() {
        return client.getLastDiagnostics();
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }
}
