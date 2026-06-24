package com.emrehalli.financeportal.news.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsCategoryRepairResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsFavoriteResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsImportanceRecalculationResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsPurgeResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Haberler", description = "Finans haberleri, KAP bildirimleri ve favoriler")
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService newsService;
    private final AppMessageSource appMessageSource;

    public NewsController(NewsService newsService, AppMessageSource appMessageSource) {
        this.newsService = newsService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Haberleri listele", description = "Anahtar kelime, sembol, kategori ve tarih aralığına göre filtrelenmiş haber akışı")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haberler başarıyla listelendi"))
    @GetMapping
    public ApiResponse<Page<NewsResponseDto>> getNews(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean isKapDisclosure,
            @RequestParam(required = false) Boolean favoritesOnly,
            @RequestParam(required = false) Long favoriteUserId,
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
                .favoritesOnly(favoritesOnly)
                .favoriteUserId(favoriteUserId)
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

    @Operation(summary = "Haber detayını getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haber detayı başarıyla getirildi"))
    @GetMapping("/{id}")
    public ApiResponse<NewsResponseDto> getNewsDetail(@PathVariable Long id) {
        NewsResponseDto response = newsService.getNewsById(id);

        return ApiResponse.<NewsResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.detail.fetched"))
                .build();
    }

    @Operation(summary = "İlişkili haberleri getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "İlişkili haberler başarıyla getirildi"))
    @GetMapping("/{id}/related")
    public ApiResponse<NewsRelatedResponseDto> getRelatedNewsData(@PathVariable Long id) {
        NewsRelatedResponseDto response = newsService.getRelatedData(id);

        return ApiResponse.<NewsRelatedResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.detail.fetched"))
                .build();
    }

    @Operation(summary = "Haberi favorilere ekle")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haber favorilere eklendi"))
    @PostMapping("/favorites/{newsId}")
    public ApiResponse<NewsFavoriteResponseDto> addCurrentUserFavorite(@PathVariable Long newsId) {
        NewsFavoriteResponseDto response = newsService.addFavoriteForCurrentUser(newsId);

        return ApiResponse.<NewsFavoriteResponseDto>builder()
                .success(true)
                .data(response)
                .message("News added to favorites")
                .build();
    }

    @Operation(summary = "Haberi favorilerden çıkar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haber favorilerden çıkarıldı"))
    @DeleteMapping("/favorites/{newsId}")
    public ApiResponse<Void> removeCurrentUserFavorite(@PathVariable Long newsId) {
        newsService.removeFavoriteForCurrentUser(newsId);

        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message("News removed from favorites")
                .build();
    }

    @Operation(summary = "Favori haberlerimi getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Favori haberler başarıyla getirildi"))
    @GetMapping("/favorites")
    public ApiResponse<List<NewsFavoriteResponseDto>> getCurrentUserFavorites() {
        List<NewsFavoriteResponseDto> response = newsService.getCurrentUserFavorites();

        return ApiResponse.<List<NewsFavoriteResponseDto>>builder()
                .success(true)
                .data(response)
                .message("News favorites fetched")
                .build();
    }

    @Operation(summary = "Kullanıcı için favori ekle")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Favori başarıyla eklendi"))
    @PostMapping("/favorites/{userId}/{newsId}")
    public ApiResponse<NewsFavoriteResponseDto> addFavorite(
            @PathVariable Long userId,
            @PathVariable Long newsId) {
        NewsFavoriteResponseDto response = newsService.addFavorite(userId, newsId);

        return ApiResponse.<NewsFavoriteResponseDto>builder()
                .success(true)
                .data(response)
                .message("News added to favorites")
                .build();
    }

    @Operation(summary = "Kullanıcı favorisini çıkar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Favori başarıyla çıkarıldı"))
    @DeleteMapping("/favorites/{userId}/{newsId}")
    public ApiResponse<Void> removeFavorite(
            @PathVariable Long userId,
            @PathVariable Long newsId) {
        newsService.removeFavorite(userId, newsId);

        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message("News removed from favorites")
                .build();
    }

    @Operation(summary = "Kullanıcının favori haberlerini getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanıcı favorileri başarıyla getirildi"))
    @GetMapping("/favorites/user/{userId}")
    public ApiResponse<List<NewsFavoriteResponseDto>> getUserFavorites(@PathVariable Long userId) {
        List<NewsFavoriteResponseDto> response = newsService.getUserFavorites(userId);

        return ApiResponse.<List<NewsFavoriteResponseDto>>builder()
                .success(true)
                .data(response)
                .message("News favorites fetched")
                .build();
    }

    @Operation(summary = "Öne çıkan haberleri getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Öne çıkan haberler başarıyla getirildi"))
    @GetMapping("/top")
    public ApiResponse<List<NewsResponseDto>> getTopNews(@RequestParam(defaultValue = "5") int size) {
        List<NewsResponseDto> response = newsService.getTopNews(size);

        return ApiResponse.<List<NewsResponseDto>>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.top.fetched"))
                .build();
    }

    @Operation(summary = "Önem puanlarını yeniden hesapla")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Önem puanları yeniden hesaplandı"))
    @PostMapping("/admin/recalculate-importance")
    public ApiResponse<NewsImportanceRecalculationResponseDto> recalculateImportanceScores() {
        NewsImportanceRecalculationResponseDto response = newsService.recalculateImportanceScores();

        return ApiResponse.<NewsImportanceRecalculationResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.importance.recalculated"))
                .build();
    }

    @Operation(summary = "Sağlayıcıya göre haberleri temizle")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haberler başarıyla temizlendi"))
    @PostMapping("/admin/purge")
    public ApiResponse<NewsPurgeResponseDto> purgeNewsByProvider(@RequestParam String provider) {
        NewsPurgeResponseDto response = newsService.purgeByProvider(provider);

        return ApiResponse.<NewsPurgeResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.sync.completed"))
                .build();
    }

    @Operation(summary = "Haber kategorilerini onar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haber kategorileri onarıldı"))
    @PostMapping("/admin/repair-categories")
    public ApiResponse<NewsCategoryRepairResponseDto> repairCategories(
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        NewsCategoryRepairResponseDto response = newsService.repairCategories(limit, dryRun);

        return ApiResponse.<NewsCategoryRepairResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.sync.completed"))
                .build();
    }

    @Operation(summary = "Haberleri senkronize et")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haberler başarıyla senkronize edildi"))
    @PostMapping("/sync")
    public ApiResponse<NewsSyncResponseDto> syncNews(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Integer limit) {

        NewsSyncResponseDto response;

        if (symbol != null && !symbol.trim().isEmpty()) {
            response = newsService.syncCompanyNews(symbol, provider, limit);
        } else {
            response = newsService.syncLatestNews(scope, provider, limit);
        }

        return ApiResponse.<NewsSyncResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("news.sync.completed"))
                .build();
    }
}

