package com.emrehalli.financeportal.news.provider.kap;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KapNewsProvider implements NewsProvider {

    private final KapNewsClient kapNewsClient;
    private final KapNewsProperties properties;

    public KapNewsProvider(KapNewsClient kapNewsClient, KapNewsProperties properties) {
        this.kapNewsClient = kapNewsClient;
        this.properties = properties;
    }

    @Override
    public String getProviderName() {
        return "KAP";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        return kapNewsClient.fetchLatestNews();
    }

    @Override
    public List<NewsItemDto> fetchCompanyNews(String symbol) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        return kapNewsClient.fetchCompanyNews(symbol);
    }
}
