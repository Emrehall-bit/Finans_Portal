package com.emrehalli.financeportal.news.provider.finnhub;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.provider.finnhub.client.FinnhubClient;
import com.emrehalli.financeportal.news.provider.finnhub.dto.FinnhubNewsResponse;
import com.emrehalli.financeportal.news.provider.finnhub.mapper.FinnhubNewsMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FinnhubNewsProvider implements NewsProvider {

    private final FinnhubClient finnhubClient;
    private final FinnhubNewsMapper finnhubNewsMapper;

    public FinnhubNewsProvider(FinnhubClient finnhubClient, FinnhubNewsMapper finnhubNewsMapper) {
        this.finnhubClient = finnhubClient;
        this.finnhubNewsMapper = finnhubNewsMapper;
    }

    @Override
    public String getProviderName() {
        return "FINNHUB";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        List<FinnhubNewsResponse> rawNews = finnhubClient.fetchGeneralNews();
        return rawNews.stream()
                .map(finnhubNewsMapper::map)
                .filter(item -> item != null)
                .toList();
    }

    @Override
    public List<NewsItemDto> fetchCompanyNews(String symbol) {
        List<FinnhubNewsResponse> rawNews = finnhubClient.fetchCompanyNews(symbol);
        return rawNews.stream()
                .map(finnhubNewsMapper::map)
                .filter(item -> item != null)
                .toList();
    }
}



