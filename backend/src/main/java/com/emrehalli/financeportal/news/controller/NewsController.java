package com.emrehalli.financeportal.news.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsImportanceRecalculationResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsPurgeResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import org.springframework.data.domain.Page;
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
    private final AppMessageSource appMessageSource;

    public NewsController(NewsService newsService, AppMessageSource appMessageSource) {
        this.newsService = newsService;
        this.appMessageSource = appMessageSource;
    }

    @GetMapping
    public ApiResponse<Page<NewsResponseDto>> getNews(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean isKapDisclosure,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "publishedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        NewsSearchRequest request = NewsSearchRequest.builder()
                .keyword(keyword)
                .symbol(symbol)
                .category(category)
                .language(language)
                .scope(scope)
                .provider(provider)
                .isKapDisclosure(isKapDisclosure)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        Page<NewsResponseDto> response = newsService.getNews(request, page, size, sortBy, sortDirection);

        return ApiResponse.<Page<NewsResponseDto>>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.listed"))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<NewsResponseDto> getNewsDetail(@PathVariable Long id) {
        NewsResponseDto response = newsService.getNewsById(id);

        return ApiResponse.<NewsResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.detail.fetched"))
                .build();
    }

    @GetMapping("/{id}/related")
    public ApiResponse<NewsRelatedResponseDto> getRelatedNewsData(@PathVariable Long id) {
        NewsRelatedResponseDto response = newsService.getRelatedData(id);

        return ApiResponse.<NewsRelatedResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.detail.fetched"))
                .build();
    }

    @GetMapping("/top")
    public ApiResponse<List<NewsResponseDto>> getTopNews(@RequestParam(defaultValue = "5") int size) {
        List<NewsResponseDto> response = newsService.getTopNews(size);

        return ApiResponse.<List<NewsResponseDto>>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.top.fetched"))
                .build();
    }

    @PostMapping("/admin/recalculate-importance")
    public ApiResponse<NewsImportanceRecalculationResponseDto> recalculateImportanceScores() {
        NewsImportanceRecalculationResponseDto response = newsService.recalculateImportanceScores();

        return ApiResponse.<NewsImportanceRecalculationResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.importance.recalculated"))
                .build();
    }

    @PostMapping("/admin/purge")
    public ApiResponse<NewsPurgeResponseDto> purgeNewsByProvider(@RequestParam String provider) {
        NewsPurgeResponseDto response = newsService.purgeByProvider(provider);

        return ApiResponse.<NewsPurgeResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.sync.completed"))
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
                .message(appMessageSource.get("news.sync.completed"))
                .build();
    }
}



