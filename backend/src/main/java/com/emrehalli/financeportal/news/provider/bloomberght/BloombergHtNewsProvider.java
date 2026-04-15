package com.emrehalli.financeportal.news.provider.bloomberght;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.bloomberght.client.BloombergHtClient;
import com.emrehalli.financeportal.news.provider.bloomberght.mapper.BloombergHtNewsMapper;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class BloombergHtNewsProvider implements NewsProvider {

    private static final Logger logger = LogManager.getLogger(BloombergHtNewsProvider.class);

    private final BloombergHtClient bloombergHtClient;
    private final BloombergHtNewsMapper bloombergHtNewsMapper;

    public BloombergHtNewsProvider(BloombergHtClient bloombergHtClient, BloombergHtNewsMapper bloombergHtNewsMapper) {
        this.bloombergHtClient = bloombergHtClient;
        this.bloombergHtNewsMapper = bloombergHtNewsMapper;
    }

    @Override
    public String getProviderName() {
        return "BLOOMBERG_HT";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        try {
            Document document = bloombergHtClient.fetchNewsDocument();
            return bloombergHtNewsMapper.map(document);
        } catch (Exception e) {
            logger.error("Failed to map Bloomberg HT news", e);
            return List.of();
        }
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
                        || containsIgnoreCase(item.getSummary(), normalized)
                        || containsIgnoreCase(item.getRelatedSymbol(), normalized))
                .toList();
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }
}
