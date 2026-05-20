package com.emrehalli.financeportal.news.provider.common;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;

import java.util.List;

public interface NewsProvider {

    String getProviderName();

    List<NewsItemDto> fetchLatestNews();

    default List<NewsItemDto> fetchLatestNews(int limit) {
        List<NewsItemDto> items = fetchLatestNews();
        if (limit <= 0 || items.size() <= limit) {
            return items;
        }
        return items.subList(0, limit);
    }

    List<NewsItemDto> fetchCompanyNews(String symbol);

    default List<NewsItemDto> fetchCompanyNews(String symbol, int limit) {
        List<NewsItemDto> items = fetchCompanyNews(symbol);
        if (limit <= 0 || items.size() <= limit) {
            return items;
        }
        return items.subList(0, limit);
    }
}



