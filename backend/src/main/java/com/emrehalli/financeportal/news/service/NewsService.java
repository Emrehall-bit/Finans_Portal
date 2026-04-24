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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
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
    public Page<NewsResponseDto> getNews(
            NewsSearchRequest request,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        validateDateRange(request);
        validatePaging(page, size);
        QueryContext context = resolveQueryContext(request);
        String resolvedSortBy = resolveSortBy(sortBy);
        Sort.Direction resolvedSortDirection = resolveSortDirection(sortDirection);
        Pageable pageable = PageRequest.of(
                page,
                size,
                resolvePageableSort(resolvedSortBy, resolvedSortDirection)
        );
        Specification<News> specification = buildSpecification(request, context, resolvedSortBy, resolvedSortDirection);

        return newsRepository.findAll(specification, pageable)
                .map(this::toResponse);
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
        int valid = 0;
        int invalid = 0;
        int duplicate = 0;
        int existing = 0;
        int saved = 0;

        for (NewsProviderType providerType : providers) {
            NewsSyncResponseDto result = syncSingleProvider(providerType, symbol);
            fetched += result.getFetchedCount();
            valid += result.getValidCount();
            invalid += result.getInvalidCount();
            duplicate += result.getDuplicateCount();
            existing += result.getExistingCount();
            saved += result.getSavedCount();
        }

        double parseSuccessRatio = fetched == 0 ? 0.0 : (double) valid / fetched;

        return NewsSyncResponseDto.builder()
                .provider(scope.name())
                .fetchedCount(fetched)
                .validCount(valid)
                .invalidCount(invalid)
                .duplicateCount(duplicate)
                .existingCount(existing)
                .savedCount(saved)
                .parseSuccessRatio(parseSuccessRatio)
                .build();
    }

    private NewsSyncResponseDto syncSingleProvider(NewsProviderType providerType, String symbol) {
        NewsProvider provider = getProvider(providerType);
        List<NewsItemDto> items = hasText(symbol)
                ? provider.fetchCompanyNews(symbol)
                : provider.fetchLatestNews();

        PersistenceStats stats = saveNewsItems(items, providerType.name());
        double parseSuccessRatio = items.isEmpty() ? 0.0 : (double) stats.validCount() / items.size();

        logger.info(
                "News sync stats. provider: {}, fetched: {}, valid: {}, invalid: {}, duplicate: {}, existing: {}, saved: {}, parseSuccessRatio: {}",
                providerType.name(),
                items.size(),
                stats.validCount(),
                stats.invalidCount(),
                stats.duplicateCount(),
                stats.existingCount(),
                stats.savedCount(),
                String.format("%.2f", parseSuccessRatio)
        );

        return NewsSyncResponseDto.builder()
                .provider(providerType.name())
                .fetchedCount(items.size())
                .validCount(stats.validCount())
                .invalidCount(stats.invalidCount())
                .duplicateCount(stats.duplicateCount())
                .existingCount(stats.existingCount())
                .savedCount(stats.savedCount())
                .parseSuccessRatio(parseSuccessRatio)
                .build();
    }

    private PersistenceStats saveNewsItems(List<NewsItemDto> items, String providerName) {
        if (items == null || items.isEmpty()) {
            logger.info("No news items fetched from provider: {}", providerName);
            return PersistenceStats.empty();
        }

        int savedCount = 0;
        int invalidCount = 0;
        int duplicateCount = 0;
        int existingCount = 0;
        int missingExternalIdCount = 0;
        int missingTitleCount = 0;
        int missingUrlCount = 0;
        int missingSourceCount = 0;
        int missingDateCount = 0;
        List<News> toSave = new ArrayList<>();
        Set<String> existingExternalIds = findExistingExternalIds(items);
        Set<String> seenExternalIds = new HashSet<>();

        for (NewsItemDto item : items) {
            ValidationResult validationResult = validateForPersistence(item);
            if (!validationResult.valid()) {
                logger.debug("Skipping invalid news item. externalId: {}", item != null ? item.getExternalId() : null);
                invalidCount++;
                missingExternalIdCount += validationResult.missingExternalId() ? 1 : 0;
                missingTitleCount += validationResult.missingTitle() ? 1 : 0;
                missingUrlCount += validationResult.missingUrl() ? 1 : 0;
                missingSourceCount += validationResult.missingSource() ? 1 : 0;
                continue;
            }

            if (item.getPublishedAt() == null) {
                missingDateCount++;
            }

            String externalId = item.getExternalId().trim();
            if (!seenExternalIds.add(externalId)) {
                logger.debug("Skipping duplicate news item within the same batch. externalId: {}", externalId);
                duplicateCount++;
                continue;
            }

            if (existingExternalIds.contains(externalId)) {
                existingCount++;
                continue;
            }

            toSave.add(News.builder()
                    .externalId(externalId)
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

        int validCount = items.size() - invalidCount;
        logger.info(
                "News persistence completed. provider: {}, fetched: {}, valid: {}, invalid: {}, duplicate: {}, existing: {}, saved: {}, invalidBecauseMissingTitle: {}, invalidBecauseMissingUrl: {}, invalidBecauseMissingSource: {}, invalidBecauseMissingExternalId: {}, invalidBecauseMissingDate: {}",
                providerName,
                items.size(),
                validCount,
                invalidCount,
                duplicateCount,
                existingCount,
                savedCount,
                missingTitleCount,
                missingUrlCount,
                missingSourceCount,
                missingExternalIdCount,
                missingDateCount
        );
        return new PersistenceStats(
                validCount,
                invalidCount,
                duplicateCount,
                existingCount,
                savedCount,
                missingExternalIdCount,
                missingTitleCount,
                missingUrlCount,
                missingSourceCount,
                missingDateCount
        );
    }

    private Set<String> findExistingExternalIds(List<NewsItemDto> items) {
        Set<String> externalIds = items.stream()
                .map(this::validateForPersistence)
                .filter(ValidationResult::valid)
                .map(ValidationResult::item)
                .map(NewsItemDto::getExternalId)
                .filter(this::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());

        if (externalIds.isEmpty()) {
            return Set.of();
        }

        return newsRepository.findByExternalIdIn(externalIds).stream()
                .map(News::getExternalId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private ValidationResult validateForPersistence(NewsItemDto item) {
        if (item == null) {
            return ValidationResult.invalid(null, true, true, true, true);
        }
        boolean missingExternalId = !hasText(item.getExternalId());
        boolean missingTitle = !hasText(item.getTitle());
        boolean missingSource = !hasText(item.getSource());
        boolean missingUrl = !hasText(item.getUrl());
        boolean missingProvider = !hasText(item.getProvider());
        boolean missingRegionScope = !hasText(item.getRegionScope());

        return new ValidationResult(
                item,
                !(missingExternalId || missingTitle || missingSource || missingUrl || missingProvider || missingRegionScope),
                missingExternalId,
                missingTitle,
                missingUrl,
                missingSource
        );
    }

    private void validateDateRange(NewsSearchRequest request) {
        if (request.getFromDate() != null && request.getToDate() != null
                && request.getFromDate().isAfter(request.getToDate())) {
            throw new BadRequestException("fromDate cannot be after toDate");
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
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

    private String resolveSortBy(String sortBy) {
        String resolvedSortBy = hasText(sortBy) ? sortBy.trim() : "publishedAt";
        return switch (resolvedSortBy) {
            case "publishedAt", "title", "source", "category", "provider", "regionScope" -> resolvedSortBy;
            default -> throw new BadRequestException(
                    "Invalid sortBy. Allowed values: publishedAt, title, source, category, provider, regionScope"
            );
        };
    }

    private Sort.Direction resolveSortDirection(String sortDirection) {
        if (!hasText(sortDirection)) {
            return Sort.Direction.DESC;
        }
        try {
            return Sort.Direction.fromString(sortDirection.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid sortDirection. Allowed values: asc, desc");
        }
    }

    private Sort resolvePageableSort(String resolvedSortBy, Sort.Direction resolvedSortDirection) {
        if ("publishedAt".equals(resolvedSortBy)) {
            return Sort.unsorted();
        }
        return Sort.by(new Sort.Order(resolvedSortDirection, resolvedSortBy));
    }

    private Specification<News> buildSpecification(
            NewsSearchRequest request,
            QueryContext context,
            String resolvedSortBy,
            Sort.Direction resolvedSortDirection
    ) {
        return Specification.allOf(
                byProvider(context),
                byScope(context),
                byCategory(request),
                byLanguage(request),
                bySymbol(request),
                byKeyword(request),
                byDateRange(request),
                bySort(resolvedSortBy, resolvedSortDirection)
        );
    }

    private Specification<News> byProvider(QueryContext context) {
        if (context.provider == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("provider"), context.provider.name());
    }

    private Specification<News> byScope(QueryContext context) {
        if (context.scope == NewsScope.ALL) {
            return null;
        }
        return (root, query, cb) -> {
            if (context.scope == NewsScope.LOCAL) {
                return cb.upper(root.get("regionScope")).in("LOCAL", "TR");
            }
            return cb.equal(root.get("regionScope"), mapScopeToRegion(context.scope));
        };
    }

    private Specification<News> byCategory(NewsSearchRequest request) {
        if (!hasText(request.getCategory())) {
            return null;
        }
        String category = request.getCategory().trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), category);
    }

    private Specification<News> byLanguage(NewsSearchRequest request) {
        if (!hasText(request.getLanguage())) {
            return null;
        }
        String language = request.getLanguage().trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("language")), language);
    }

    private Specification<News> bySymbol(NewsSearchRequest request) {
        if (!hasText(request.getSymbol())) {
            return null;
        }
        String symbol = normalizeSymbol(request.getSymbol());
        return (root, query, cb) -> cb.equal(cb.upper(root.get("relatedSymbol")), symbol);
    }

    private Specification<News> byKeyword(NewsSearchRequest request) {
        if (!hasText(request.getKeyword())) {
            return null;
        }
        String keyword = "%" + request.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), keyword),
                cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), keyword)
        );
    }

    private Specification<News> byDateRange(NewsSearchRequest request) {
        if (request.getFromDate() == null && request.getToDate() == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (request.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("publishedAt"),
                        request.getFromDate().atStartOfDay()
                ));
            }
            if (request.getToDate() != null) {
                predicates.add(cb.lessThan(
                        root.get("publishedAt"),
                        request.getToDate().plusDays(1).atStartOfDay()
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<News> bySort(String resolvedSortBy, Sort.Direction resolvedSortDirection) {
        if (!"publishedAt".equals(resolvedSortBy)) {
            return null;
        }

        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                query.orderBy(
                        cb.asc(cb.selectCase().when(cb.isNull(root.get("publishedAt")), 1).otherwise(0)),
                        resolvedSortDirection.isAscending()
                                ? cb.asc(root.get("publishedAt"))
                                : cb.desc(root.get("publishedAt")),
                        cb.desc(root.get("createdAt"))
                );
            }
            return cb.conjunction();
        };
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

    private record ValidationResult(
            NewsItemDto item,
            boolean valid,
            boolean missingExternalId,
            boolean missingTitle,
            boolean missingUrl,
            boolean missingSource
    ) {
        private static ValidationResult invalid(
                NewsItemDto item,
                boolean missingExternalId,
                boolean missingTitle,
                boolean missingUrl,
                boolean missingSource
        ) {
            return new ValidationResult(item, false, missingExternalId, missingTitle, missingUrl, missingSource);
        }
    }

    private record PersistenceStats(
            int validCount,
            int invalidCount,
            int duplicateCount,
            int existingCount,
            int savedCount,
            int missingExternalIdCount,
            int missingTitleCount,
            int missingUrlCount,
            int missingSourceCount,
            int missingDateCount
    ) {
        private static PersistenceStats empty() {
            return new PersistenceStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
