package com.emrehalli.financeportal.news.provider.aa;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.aa.client.AaRssNewsClient;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnosticsAware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AaRssNewsProvider implements NewsProvider, ProviderSyncDiagnosticsAware {

    private static final Logger logger = LogManager.getLogger(AaRssNewsProvider.class);

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

        List<AaNewsProperties.FeedConfig> feeds = properties.getEffectiveFeeds();
        List<String> feedNames = feeds.stream()
                .map(f -> f.getName() != null ? f.getName() : "unknown")
                .toList();
        logger.info("AA RSS provider starting fetch. provider=AA_RSS, feeds={}", feedNames);

        List<NewsItemDto> combined = new ArrayList<>();

        for (AaNewsProperties.FeedConfig feed : feeds) {
            String feedName = feed.getName() != null ? feed.getName() : "unknown";
            String feedUrl = feed.getRssUrl();
            String feedCategory = feed.getDefaultCategory() != null
                    ? feed.getDefaultCategory()
                    : properties.getDefaultCategory();
            try {
                List<NewsItemDto> feedItems = aaRssNewsClient.fetchFeed(feedName, feedUrl, feedCategory);
                combined.addAll(feedItems);
                logger.debug("AA feed processed. provider=AA_RSS, feed={}, url={}, count={}",
                        feedName, feedUrl, feedItems.size());
            } catch (Exception e) {
                logger.warn("AA feed processing failed, skipping. provider=AA_RSS, feed={}, url={}",
                        feedName, feedUrl, e);
            }
        }

        return combined;
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
        return aaRssNewsClient.getLastDiagnostics();
    }
}
