package com.emrehalli.financeportal.news.provider.cnbc;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnosticsAware;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class CnbcRssNewsProvider implements NewsProvider, ProviderSyncDiagnosticsAware {

    private final CnbcRssNewsClient client;

    public CnbcRssNewsProvider(CnbcRssNewsClient client, CnbcNewsProperties properties) {
        this.client = client;
    }

    @Override
    public String getProviderName() {
        return "CNBC_RSS";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        return client.fetchNewsWithDiagnostics().getItems();
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

    @Override
    public ProviderSyncDiagnostics getLastDiagnostics() {
        return client.getLastDiagnostics();
    }
}




