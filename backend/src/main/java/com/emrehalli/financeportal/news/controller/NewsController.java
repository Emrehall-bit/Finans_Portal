package com.emrehalli.financeportal.news.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public ApiResponse<List<NewsResponseDto>> getNews(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        NewsSearchRequest request = NewsSearchRequest.builder()
                .keyword(keyword)
                .symbol(symbol)
                .category(category)
                .scope(scope)
                .provider(provider)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        List<NewsResponseDto> response = newsService.getNews(request);

        return ApiResponse.<List<NewsResponseDto>>builder()
                .success(true)
                .data(response)
                .message("News listed successfully")
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<NewsResponseDto> getNewsDetail(@PathVariable Long id) {
        NewsResponseDto response = newsService.getNewsById(id);

        return ApiResponse.<NewsResponseDto>builder()
                .success(true)
                .data(response)
                .message("News detail fetched successfully")
                .build();
    }

    @GetMapping("/symbol/{symbol}")
    public ApiResponse<List<NewsResponseDto>> getNewsBySymbol(@PathVariable String symbol) {
        NewsSearchRequest request = NewsSearchRequest.builder()
                .symbol(symbol)
                .build();

        List<NewsResponseDto> response = newsService.getNews(request);

        return ApiResponse.<List<NewsResponseDto>>builder()
                .success(true)
                .data(response)
                .message("Symbol news listed successfully")
                .build();
    }

    @PostMapping("/sync")
    public ApiResponse<NewsSyncResponseDto> syncNews(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String symbol) {

        NewsSyncResponseDto response;

        if (symbol != null && !symbol.trim().isEmpty()) {
            response = newsService.syncCompanyNews(symbol, provider);
        } else {
            response = newsService.syncLatestNews(scope, provider);
        }

        return ApiResponse.<NewsSyncResponseDto>builder()
                .success(true)
                .data(response)
                .message("News sync completed")
                .build();
    }
}