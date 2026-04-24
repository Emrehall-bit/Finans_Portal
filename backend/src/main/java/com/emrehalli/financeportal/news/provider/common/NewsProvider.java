package com.emrehalli.financeportal.news.provider.common;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;

import java.util.List;

public interface NewsProvider {

    String getProviderName();

    List<NewsItemDto> fetchLatestNews();

    List<NewsItemDto> fetchCompanyNews(String symbol);
}



