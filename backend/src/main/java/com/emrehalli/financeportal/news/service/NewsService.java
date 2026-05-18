package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import com.emrehalli.financeportal.news.config.NewsNotificationProperties;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.dto.response.NewsImportanceRecalculationResponseDto;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NewsService {

    private static final Logger logger = LogManager.getLogger(NewsService.class);

    private final NewsRepository newsRepository;
    private final Map<String, NewsProvider> providerMap;
    private final NewsImportanceScoringService newsImportanceScoringService;
    private final NotificationService notificationService;
    private final NewsNotificationProperties notificationProperties;

    @Autowired
    public NewsService(
            NewsRepository newsRepository,
            List<NewsProvider> providers,
            NewsImportanceScoringService newsImportanceScoringService,
            NotificationService notificationService,
            NewsNotificationProperties notificationProperties
    ) {
        this.newsRepository = newsRepository;
        this.newsImportanceScoringService = newsImportanceScoringService;
        this.notificationService = notificationService;
        this.notificationProperties = notificationProperties;
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

    @Transactional(readOnly = true)
    public List<NewsResponseDto> getTopNews(int size) {
        validatePaging(0, size);
        return getNews(NewsSearchRequest.builder().build(), 0, size, "importanceScore", "desc").getContent();
    }

    @Transactional
    public NewsImportanceRecalculationResponseDto recalculateImportanceScores() {
        final int CHUNK_SIZE = 500;
        int page = 0;
        int totalProcessed = 0;
        int totalUpdated = 0;
        int minScore = Integer.MAX_VALUE;
        int maxScore = Integer.MIN_VALUE;
        long scoreSum = 0;

        Page<News> chunk;
        do {
            chunk = newsRepository.findAll(PageRequest.of(page, CHUNK_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            List<News> toUpdate = new ArrayList<>();

            for (News news : chunk.getContent()) {
                int recalculated = newsImportanceScoringService.calculateScore(news);
                if (!java.util.Objects.equals(news.getImportanceScore(), recalculated)) {
                    news.setImportanceScore(recalculated);
                    toUpdate.add(news);
                    totalUpdated++;
                }
                int score = news.getImportanceScore() != null ? news.getImportanceScore() : 0;
                minScore = Math.min(minScore, score);
                maxScore = Math.max(maxScore, score);
                scoreSum += score;
            }

            if (!toUpdate.isEmpty()) {
                newsRepository.saveAll(toUpdate);
            }
            totalProcessed += chunk.getNumberOfElements();
            logger.info("Importance score recalculation chunk. page: {}/{}, chunkSize: {}, updatedInChunk: {}",
                    page + 1, chunk.getTotalPages(), chunk.getNumberOfElements(), toUpdate.size());
            page++;
        } while (chunk.hasNext());

        int finalMin = totalProcessed == 0 ? 0 : (minScore == Integer.MAX_VALUE ? 0 : minScore);
        int finalMax = totalProcessed == 0 ? 0 : (maxScore == Integer.MIN_VALUE ? 0 : maxScore);
        double averageScore = totalProcessed == 0 ? 0.0 : (double) scoreSum / totalProcessed;

        NewsImportanceRecalculationResponseDto response = NewsImportanceRecalculationResponseDto.builder()
                .totalProcessed(totalProcessed)
                .updatedCount(totalUpdated)
                .minScore(finalMin)
                .maxScore(finalMax)
                .averageScore(averageScore)
                .build();

        logger.info("News importance score recalculation completed. totalProcessed: {}, updatedCount: {}, minScore: {}, maxScore: {}, averageScore: {}",
                response.getTotalProcessed(), response.getUpdatedCount(), response.getMinScore(), response.getMaxScore(),
                String.format("%.2f", response.getAverageScore()));

        return response;
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
        long startedAt = System.nanoTime();
        LoggingContext.put(LoggingConstants.PROVIDER_NAME_KEY, providerType.name());

        try {
            List<NewsItemDto> items = hasText(symbol)
                    ? provider.fetchCompanyNews(symbol)
                    : provider.fetchLatestNews();

            PersistenceStats stats = saveNewsItems(items, providerType.name());
            double parseSuccessRatio = items.isEmpty() ? 0.0 : (double) stats.validCount() / items.size();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

            LoggingContext.put(LoggingConstants.SUCCESS_KEY, Boolean.TRUE.toString());
            LoggingContext.put(LoggingConstants.DURATION_MS_KEY, String.valueOf(durationMs));
            LoggingContext.put(LoggingConstants.FETCHED_ITEM_COUNT_KEY, String.valueOf(items.size()));

            logger.info(
                    "News sync stats. provider: {}, fetched: {}, valid: {}, invalid: {}, duplicate: {}, existing: {}, saved: {}, parseSuccessRatio: {}, durationMs: {}",
                    providerType.name(),
                    items.size(),
                    stats.validCount(),
                    stats.invalidCount(),
                    stats.duplicateCount(),
                    stats.existingCount(),
                    stats.savedCount(),
                    String.format("%.2f", parseSuccessRatio),
                    durationMs
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
        } catch (Exception ex) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            LoggingContext.put(LoggingConstants.SUCCESS_KEY, Boolean.FALSE.toString());
            LoggingContext.put(LoggingConstants.DURATION_MS_KEY, String.valueOf(durationMs));
            logger.error(
                    "News provider sync failed. provider: {}, symbol: {}, durationMs: {}, message: {}",
                    providerType.name(),
                    symbol,
                    durationMs,
                    ex.getMessage(),
                    ex
            );
            return NewsSyncResponseDto.builder()
                    .provider(providerType.name())
                    .fetchedCount(0)
                    .validCount(0)
                    .invalidCount(0)
                    .duplicateCount(0)
                    .existingCount(0)
                    .savedCount(0)
                    .parseSuccessRatio(0.0)
                    .build();
        } finally {
            LoggingContext.remove(LoggingConstants.PROVIDER_NAME_KEY);
            LoggingContext.remove(LoggingConstants.SUCCESS_KEY);
            LoggingContext.remove(LoggingConstants.DURATION_MS_KEY);
            LoggingContext.remove(LoggingConstants.FETCHED_ITEM_COUNT_KEY);
        }
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
        Map<String, News> existingNewsByExternalId = findExistingNewsByExternalId(items);
        Set<String> existingExternalIds = existingNewsByExternalId.keySet();
        Set<String> seenExternalIds = new HashSet<>();
        List<News> existingToUpdate = new ArrayList<>();

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
                News existingNews = existingNewsByExternalId.get(externalId);
                if (existingNews != null) {
                    boolean needsUpdate = false;
                    if (hasLowValueLogoImage(existingNews.getImageUrl())) {
                        if (hasText(item.getImageUrl())) {
                            existingNews.setImageUrl(item.getImageUrl().trim());
                        } else {
                            existingNews.setImageUrl(null);
                        }
                        needsUpdate = true;
                    } else if (!hasText(existingNews.getImageUrl()) && hasText(item.getImageUrl())) {
                        existingNews.setImageUrl(item.getImageUrl().trim());
                        needsUpdate = true;
                    }
                    if (!hasText(existingNews.getSummary()) && hasText(item.getSummary())) {
                        existingNews.setSummary(item.getSummary().trim());
                        needsUpdate = true;
                    }
                    if (existingNews.getPublishedAt() == null && item.getPublishedAt() != null) {
                        existingNews.setPublishedAt(item.getPublishedAt());
                        needsUpdate = true;
                    }
                    if (shouldRefreshImportanceScore(existingNews)) {
                        int recalculatedScore = newsImportanceScoringService.calculateScore(existingNews);
                        if (!java.util.Objects.equals(existingNews.getImportanceScore(), recalculatedScore)) {
                            existingNews.setImportanceScore(recalculatedScore);
                            needsUpdate = true;
                        }
                    }
                    if (needsUpdate) {
                        existingToUpdate.add(existingNews);
                    }
                }
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
                    .imageUrl(item.getImageUrl())
                    .publishedAt(item.getPublishedAt())
                    .importanceScore(0)
                    .build());
        }

        toSave.forEach(news -> news.setImportanceScore(newsImportanceScoringService.calculateScore(news)));

        if (!toSave.isEmpty()) {
            List<News> saved = newsRepository.saveAll(toSave);
            savedCount = saved.size();
            if (notificationProperties.isEnabled()) {
                int threshold = notificationProperties.getMinImportanceScore();
                saved.stream()
                        .filter(news -> news.getImportanceScore() != null && news.getImportanceScore() >= threshold)
                        .forEach(news -> {
                            String title = truncate(news.getTitle(), 255);
                            String message = hasText(news.getSummary())
                                    ? truncate(news.getSummary(), 1000)
                                    : title;
                            try {
                                notificationService.createSystemBroadcastNotification(title, message);
                            } catch (Exception ex) {
                                logger.warn("Failed to send news notification. newsId: {}, reason: {}", news.getId(), ex.getMessage());
                            }
                        });
            }
        }
        if (!existingToUpdate.isEmpty()) {
            newsRepository.saveAll(existingToUpdate);
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

    private Map<String, News> findExistingNewsByExternalId(List<NewsItemDto> items) {
        Set<String> externalIds = items.stream()
                .map(this::validateForPersistence)
                .filter(ValidationResult::valid)
                .map(ValidationResult::item)
                .map(NewsItemDto::getExternalId)
                .filter(this::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());

        if (externalIds.isEmpty()) {
            return Map.of();
        }

        return newsRepository.findByExternalIdIn(externalIds).stream()
                .collect(java.util.stream.Collectors.toMap(News::getExternalId, news -> news));
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

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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
            case "publishedAt", "importanceScore", "title", "source", "category", "provider", "regionScope" -> resolvedSortBy;
            default -> throw new BadRequestException(
                    "Invalid sortBy. Allowed values: publishedAt, importanceScore, title, source, category, provider, regionScope"
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
        if ("publishedAt".equals(resolvedSortBy) || "importanceScore".equals(resolvedSortBy)) {
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
        String titlePattern = "%" + symbol.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.equal(cb.upper(root.get("relatedSymbol")), symbol),
                cb.and(
                        cb.isNull(root.get("relatedSymbol")),
                        cb.like(cb.lower(root.get("title")), titlePattern)
                )
        );
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
        if (!"publishedAt".equals(resolvedSortBy) && !"importanceScore".equals(resolvedSortBy)) {
            return null;
        }

        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                if ("importanceScore".equals(resolvedSortBy)) {
                    query.orderBy(
                            resolvedSortDirection.isAscending()
                                    ? cb.asc(root.get("importanceScore"))
                                    : cb.desc(root.get("importanceScore")),
                            cb.asc(cb.selectCase().when(cb.isNull(root.get("publishedAt")), 1).otherwise(0)),
                            cb.desc(root.get("publishedAt")),
                            cb.desc(root.get("createdAt"))
                    );
                } else {
                    query.orderBy(
                            cb.asc(cb.selectCase().when(cb.isNull(root.get("publishedAt")), 1).otherwise(0)),
                            resolvedSortDirection.isAscending()
                                    ? cb.asc(root.get("publishedAt"))
                                    : cb.desc(root.get("publishedAt")),
                            cb.desc(root.get("createdAt"))
                    );
                }
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

    private boolean shouldRefreshImportanceScore(News news) {
        return news.getImportanceScore() == null || news.getImportanceScore() <= 0;
    }

    private boolean hasLowValueLogoImage(String imageUrl) {
        if (!hasText(imageUrl)) {
            return false;
        }
        String normalized = imageUrl.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("/logo/")
                || normalized.contains("_logo.")
                || normalized.endsWith("logo.jpeg")
                || normalized.endsWith("logo.jpg")
                || normalized.endsWith("logo.png")
                || normalized.endsWith("logo.webp");
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
                .imageUrl(news.getImageUrl())
                .publishedAt(news.getPublishedAt())
                .importanceScore(news.getImportanceScore())
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
