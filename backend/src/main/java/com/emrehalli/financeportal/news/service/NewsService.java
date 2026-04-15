package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsScope;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NewsService {

    private static final Logger logger = LogManager.getLogger(NewsService.class);

    private final NewsRepository newsRepository;
    private final Map<String, NewsProvider> providerMap;

    public NewsService(NewsRepository newsRepository, List<NewsProvider> providers) {
        this.newsRepository = newsRepository;
        this.providerMap = new HashMap<>();
        for (NewsProvider provider : providers) {
            providerMap.put(provider.getProviderName(), provider);
        }
    }

    @Transactional(readOnly = true)
    public List<NewsResponseDto> getNews(NewsSearchRequest request) {
        validateDateRange(request);
        QueryContext context = resolveQueryContext(request);

        List<News> baseList;
        if (hasText(request.getSymbol())) {
            String symbol = normalizeSymbol(request.getSymbol());
            baseList = queryBySymbol(context, symbol);
        } else if (hasText(request.getCategory())) {
            String category = request.getCategory().trim();
            baseList = queryByCategory(context, category);
        } else if (hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            baseList = queryByKeyword(context, keyword);
        } else {
            baseList = queryBase(context);
        }

        return baseList.stream()
                .filter(news -> matchesDateRange(news, request))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NewsResponseDto getNewsById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + id));
        return toResponse(news);
    }

    @Transactional
    public NewsSyncResponseDto syncLatestNews(String scope, String provider) {
        if (hasText(provider)) {
            NewsProviderType providerType = NewsProviderType.from(provider);
            return syncSingleProvider(providerType, null);
        }

        NewsScope newsScope = NewsScope.from(scope);
        return syncByScope(newsScope, null);
    }

    @Transactional
    public NewsSyncResponseDto syncCompanyNews(String symbol, String provider) {
        if (!hasText(provider)) {
            throw new BadRequestException("provider is required for symbol based sync");
        }
        String normalizedSymbol = normalizeSymbol(symbol);
        NewsProviderType providerType = NewsProviderType.from(provider);
        return syncSingleProvider(providerType, normalizedSymbol);
    }

    @Transactional
    public NewsSyncResponseDto syncProvider(NewsProviderType providerType) {
        return syncSingleProvider(providerType, null);
    }

    @Transactional
    public NewsSyncResponseDto syncByScope(NewsScope scope, String symbol) {
        Set<NewsProviderType> providers = scope.providers();
        int fetched = 0;
        int saved = 0;

        for (NewsProviderType providerType : providers) {
            NewsSyncResponseDto result = syncSingleProvider(providerType, symbol);
            fetched += result.getFetchedCount();
            saved += result.getSavedCount();
        }

        return NewsSyncResponseDto.builder()
                .provider(scope.name())
                .fetchedCount(fetched)
                .savedCount(saved)
                .build();
    }

    private NewsSyncResponseDto syncSingleProvider(NewsProviderType providerType, String symbol) {
        NewsProvider provider = getProvider(providerType);
        List<NewsItemDto> items = hasText(symbol)
                ? provider.fetchCompanyNews(symbol)
                : provider.fetchLatestNews();

        int savedCount = saveNewsItems(items, providerType.name());
        return NewsSyncResponseDto.builder()
                .provider(providerType.name())
                .fetchedCount(items.size())
                .savedCount(savedCount)
                .build();
    }

    private int saveNewsItems(List<NewsItemDto> items, String providerName) {
        if (items == null || items.isEmpty()) {
            logger.info("No news items fetched from provider: {}", providerName);
            return 0;
        }

        int savedCount = 0;
        List<News> toSave = new ArrayList<>();

        for (NewsItemDto item : items) {
            if (!isValidForPersistence(item)) {
                logger.debug("Skipping invalid news item. externalId: {}", item != null ? item.getExternalId() : null);
                continue;
            }

            if (newsRepository.existsByExternalId(item.getExternalId())) {
                continue;
            }

            toSave.add(News.builder()
                    .externalId(item.getExternalId())
                    .title(item.getTitle().trim())
                    .summary(item.getSummary())
                    .source(item.getSource())
                    .provider(item.getProvider())
                    .language(item.getLanguage())
                    .regionScope(item.getRegionScope())
                    .category(item.getCategory())
                    .relatedSymbol(item.getRelatedSymbol())
                    .url(item.getUrl())
                    .publishedAt(item.getPublishedAt())
                    .build());
        }

        if (!toSave.isEmpty()) {
            savedCount = newsRepository.saveAll(toSave).size();
        }

        logger.info("News sync completed. provider: {}, fetched: {}, saved: {}",
                providerName, items.size(), savedCount);
        return savedCount;
    }

    private boolean isValidForPersistence(NewsItemDto item) {
        if (item == null) {
            return false;
        }
        if (!hasText(item.getExternalId())) {
            return false;
        }
        if (!hasText(item.getTitle())) {
            return false;
        }
        if (!hasText(item.getProvider())) {
            return false;
        }
        if (!hasText(item.getRegionScope())) {
            return false;
        }
        if (!hasText(item.getUrl())) {
            return false;
        }
        return item.getPublishedAt() != null;
    }

    private boolean matchesDateRange(News news, NewsSearchRequest request) {
        if (request.getFromDate() != null) {
            LocalDateTime from = request.getFromDate().atStartOfDay();
            if (news.getPublishedAt().isBefore(from)) {
                return false;
            }
        }
        if (request.getToDate() != null) {
            LocalDateTime to = request.getToDate().plusDays(1).atStartOfDay();
            if (!news.getPublishedAt().isBefore(to)) {
                return false;
            }
        }
        return true;
    }

    private void validateDateRange(NewsSearchRequest request) {
        if (request.getFromDate() != null && request.getToDate() != null
                && request.getFromDate().isAfter(request.getToDate())) {
            throw new BadRequestException("fromDate cannot be after toDate");
        }
    }

    private String normalizeSymbol(String symbol) {
        if (!hasText(symbol)) {
            throw new BadRequestException("symbol cannot be blank");
        }
        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private QueryContext resolveQueryContext(NewsSearchRequest request) {
        NewsProviderType provider = hasText(request.getProvider()) ? NewsProviderType.from(request.getProvider()) : null;
        NewsScope scope = NewsScope.from(request.getScope());

        if (provider != null && !scope.providers().contains(provider)) {
            throw new BadRequestException("Selected provider does not match selected scope");
        }

        return new QueryContext(scope, provider);
    }

    private List<News> queryBase(QueryContext context) {
        if (context.provider != null) {
            return newsRepository.findAllByProviderOrderByPublishedAtDesc(context.provider.name());
        }
        if (context.scope == NewsScope.ALL) {
            return newsRepository.findAllByOrderByPublishedAtDesc();
        }
        return newsRepository.findAllByRegionScopeOrderByPublishedAtDesc(mapScopeToRegion(context.scope));
    }

    private List<News> queryByCategory(QueryContext context, String category) {
        if (context.provider != null) {
            return newsRepository.findAllByProviderAndCategoryIgnoreCaseOrderByPublishedAtDesc(context.provider.name(), category);
        }
        if (context.scope == NewsScope.ALL) {
            return newsRepository.findAllByCategoryIgnoreCaseOrderByPublishedAtDesc(category);
        }
        return newsRepository.findAllByRegionScopeAndCategoryIgnoreCaseOrderByPublishedAtDesc(mapScopeToRegion(context.scope), category);
    }

    private List<News> queryBySymbol(QueryContext context, String symbol) {
        if (context.provider != null) {
            return newsRepository.findAllByProviderAndRelatedSymbolIgnoreCaseOrderByPublishedAtDesc(context.provider.name(), symbol);
        }
        if (context.scope == NewsScope.ALL) {
            return newsRepository.findAllByRelatedSymbolIgnoreCaseOrderByPublishedAtDesc(symbol);
        }
        return newsRepository.findAllByRegionScopeAndRelatedSymbolIgnoreCaseOrderByPublishedAtDesc(mapScopeToRegion(context.scope), symbol);
    }

    private List<News> queryByKeyword(QueryContext context, String keyword) {
        if (context.provider != null) {
            return newsRepository
                    .findAllByProviderAndTitleContainingIgnoreCaseOrProviderAndSummaryContainingIgnoreCaseOrderByPublishedAtDesc(
                            context.provider.name(), keyword, context.provider.name(), keyword
                    );
        }
        if (context.scope == NewsScope.ALL) {
            return newsRepository.findAllByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCaseOrderByPublishedAtDesc(keyword, keyword);
        }
        String region = mapScopeToRegion(context.scope);
        return newsRepository
                .findAllByRegionScopeAndTitleContainingIgnoreCaseOrRegionScopeAndSummaryContainingIgnoreCaseOrderByPublishedAtDesc(
                        region, keyword, region, keyword
                );
    }

    private String mapScopeToRegion(NewsScope scope) {
        return switch (scope) {
            case LOCAL -> "LOCAL";
            case GLOBAL -> "GLOBAL";
            case ALL -> throw new BadRequestException("ALL scope cannot be mapped to single region");
        };
    }

    private NewsProvider getProvider(NewsProviderType providerType) {
        NewsProvider provider = providerMap.get(providerType.name());
        if (provider == null) {
            throw new BadRequestException("Provider is not configured: " + providerType.name());
        }
        return provider;
    }

    private NewsResponseDto toResponse(News news) {
        return NewsResponseDto.builder()
                .id(news.getId())
                .externalId(news.getExternalId())
                .title(news.getTitle())
                .summary(news.getSummary())
                .source(news.getSource())
                .provider(news.getProvider())
                .language(news.getLanguage())
                .regionScope(news.getRegionScope())
                .category(news.getCategory())
                .relatedSymbol(news.getRelatedSymbol())
                .url(news.getUrl())
                .publishedAt(news.getPublishedAt())
                .build();
    }

    private record QueryContext(NewsScope scope, NewsProviderType provider) {
    }
}
