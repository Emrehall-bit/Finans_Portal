package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import com.emrehalli.financeportal.news.config.NewsNotificationProperties;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.dto.response.NewsImportanceRecalculationResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsPurgeResponseDto;
import com.emrehalli.financeportal.news.dto.response.RelatedInstrumentDto;
import com.emrehalli.financeportal.news.dto.response.RelatedNewsItemDto;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.entity.NewsProviderSyncState;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsScope;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnosticsAware;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import com.emrehalli.financeportal.news.repository.NewsProviderSyncStateRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger logger = LogManager.getLogger(NewsService.class);
    private static final int TITLE_MAX_LENGTH = 500;
    private static final int SOURCE_MAX_LENGTH = 100;
    private static final int PROVIDER_MAX_LENGTH = 100;
    private static final int LANGUAGE_MAX_LENGTH = 10;
    private static final int REGION_SCOPE_MAX_LENGTH = 20;
    private static final int CATEGORY_MAX_LENGTH = 100;
    private static final int RELATED_SYMBOL_MAX_LENGTH = 30;
    private static final int QUALITY_STATUS_MAX_LENGTH = 40;
    private static final int DISCLOSURE_TYPE_MAX_LENGTH = 50;
    private static final int MAX_RELATED_NEWS = 4;
    private static final int MAX_RELATED_INSTRUMENTS = 6;
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Map<String, InstrumentAlias> BIST_INSTRUMENT_ALIASES = createInstrumentAliases();
    private static final List<ThemeRule> THEME_RULES = createThemeRules();

    private final NewsRepository newsRepository;
    private final NewsProviderSyncStateRepository newsProviderSyncStateRepository;
    private final Map<String, NewsProvider> providerMap;
    private final NewsImportanceScoringService newsImportanceScoringService;
    private final NotificationService notificationService;
    private final NewsNotificationProperties notificationProperties;
    private final NewsPresentationMapper newsPresentationMapper;
    private final MarketQueryService marketQueryService;

    @Autowired
    public NewsService(
            NewsRepository newsRepository,
            NewsProviderSyncStateRepository newsProviderSyncStateRepository,
            List<NewsProvider> providers,
            NewsImportanceScoringService newsImportanceScoringService,
            NotificationService notificationService,
            NewsNotificationProperties notificationProperties,
            NewsPresentationMapper newsPresentationMapper,
            MarketQueryService marketQueryService
    ) {
        this.newsRepository = newsRepository;
        this.newsProviderSyncStateRepository = newsProviderSyncStateRepository;
        this.newsImportanceScoringService = newsImportanceScoringService;
        this.notificationService = notificationService;
        this.notificationProperties = notificationProperties;
        this.newsPresentationMapper = newsPresentationMapper;
        this.marketQueryService = marketQueryService;
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
    public NewsRelatedResponseDto getRelatedData(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + id));

        List<RelatedInstrumentDto> relatedInstruments = resolveRelatedInstruments(news);
        List<RelatedNewsItemDto> relatedNews = resolveRelatedNews(news, relatedInstruments);

        return NewsRelatedResponseDto.builder()
                .relatedInstruments(relatedInstruments)
                .relatedNews(relatedNews)
                .build();
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
    public NewsSyncResponseDto syncLatestNews(String scope, String provider, Integer limit) {
        if (hasText(provider)) {
            NewsProviderType providerType = NewsProviderType.from(provider);
            return syncSingleProvider(providerType, null, false, false, limit);
        }

        NewsScope newsScope = NewsScope.from(scope);
        return syncByScope(newsScope, null);
    }

    @Transactional
    public NewsSyncResponseDto syncCompanyNews(String symbol, String provider, Integer limit) {
        if (!hasText(provider)) {
            throw new BadRequestException("provider is required for symbol based sync");
        }
        String normalizedSymbol = normalizeSymbol(symbol);
        NewsProviderType providerType = NewsProviderType.from(provider);
        return syncSingleProvider(providerType, normalizedSymbol, false, false, limit);
    }

    @Transactional
    public NewsPurgeResponseDto purgeByProvider(String provider) {
        NewsProviderType providerType = NewsProviderType.from(provider);
        long deletedCount = newsRepository.deleteByProvider(providerType.name());
        logger.warn("News records purged for provider {}. deletedCount={}", providerType.name(), deletedCount);
        return NewsPurgeResponseDto.builder()
                .provider(providerType.name())
                .deletedCount(deletedCount)
                .build();
    }

    @Transactional
    public NewsSyncResponseDto syncProvider(NewsProviderType providerType) {
        return syncSingleProvider(providerType, null, false, true, null);
    }

    @Transactional
    public NewsSyncResponseDto syncProviderOnStartup(NewsProviderType providerType) {
        return syncSingleProvider(providerType, null, true, false, null);
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
            NewsSyncResponseDto result = syncSingleProvider(providerType, symbol, false, false, null);
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
                .enabled(true)
                  .feedUrlCount(providers.size())
                  .fetched(fetched)
                  .fetchedFromFeed(fetched)
                  .fetchedFromApi(fetched)
                  .canonicalResolved(0)
                  .canonicalFailed(0)
                  .extractedFullContent(saved)
                  .skippedFullContentNotAvailable(invalid)
                  .skippedCanonicalNotResolved(0)
                  .skippedByRelevance(0)
                  .duplicateSkipped(duplicate)
                  .apiQuotaLeft(null)
                  .apiQuotaUsed(null)
                  .apiQuotaRequest(null)
                  .startupSync(false)
                  .errorMessage(null)
                  .lastErrors(List.of())
                .fetchedCount(fetched)
                .validCount(valid)
                .invalidCount(invalid)
                .duplicateCount(duplicate)
                .existingCount(existing)
                .savedCount(saved)
                .parseSuccessRatio(parseSuccessRatio)
                .build();
    }

    private NewsSyncResponseDto syncSingleProvider(
            NewsProviderType providerType,
            String symbol,
            boolean startupSync,
            boolean schedulerSync,
            Integer requestedLimit
    ) {
        NewsProvider provider = getProvider(providerType);
        int effectiveLimit = resolveEffectiveLimit(providerType, startupSync, schedulerSync, requestedLimit);
        LocalDateTime lastSuccessfulSyncAt = getLastSuccessfulSyncAt(providerType);
        long startedAt = System.nanoTime();
        LoggingContext.put(LoggingConstants.PROVIDER_NAME_KEY, providerType.name());

        try {
            List<NewsItemDto> items = fetchItems(provider, providerType, symbol, effectiveLimit, schedulerSync ? lastSuccessfulSyncAt : null);
            ProviderSyncDiagnostics diagnostics = resolveProviderDiagnostics(provider, providerType);

            PersistenceStats stats = saveNewsItems(items, providerType.name());
            double parseSuccessRatio = items.isEmpty() ? 0.0 : (double) stats.validCount() / items.size();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

            LoggingContext.put(LoggingConstants.SUCCESS_KEY, Boolean.TRUE.toString());
            LoggingContext.put(LoggingConstants.DURATION_MS_KEY, String.valueOf(durationMs));
            LoggingContext.put(LoggingConstants.FETCHED_ITEM_COUNT_KEY, String.valueOf(items.size()));

            logger.info(
                    "News sync stats. provider: {}, startupSync: {}, schedulerSync: {}, enabled: {}, feedUrlCount: {}, requestedLimit: {}, effectiveLimit: {}, lastSuccessfulSyncAt: {}, fetched: {}, skippedByRelevance: {}, valid: {}, invalid: {}, duplicate: {}, existing: {}, saved: {}, parseSuccessRatio: {}, durationMs: {}, errorMessage: {}",
                    providerType.name(),
                    startupSync,
                    schedulerSync,
                    diagnostics.isEnabled(),
                    diagnostics.getFeedUrlCount(),
                    requestedLimit,
                    effectiveLimit,
                    lastSuccessfulSyncAt,
                    items.size(),
                    diagnostics.getSkippedByRelevance(),
                    stats.validCount(),
                    stats.invalidCount(),
                    stats.duplicateCount(),
                    stats.existingCount(),
                    stats.savedCount(),
                    String.format("%.2f", parseSuccessRatio),
                    durationMs,
                    diagnostics.getErrorMessage()
            );

            NewsSyncResponseDto response = NewsSyncResponseDto.builder()
                    .provider(providerType.name())
                    .enabled(diagnostics.isEnabled())
                    .feedUrlCount(diagnostics.getFeedUrlCount())
                    .fetched(items.size())
                    .fetchedFromFeed(diagnostics.getFetchedFromFeed())
                    .fetchedFromApi(diagnostics.getFetchedFromApi())
                    .canonicalResolved(diagnostics.getCanonicalResolved())
                    .canonicalFailed(diagnostics.getCanonicalFailed())
                    .extractedFullContent(diagnostics.getExtractedFullContent())
                    .skippedFullContentNotAvailable(diagnostics.getSkippedFullContentNotAvailable() + stats.skippedFullContentNotAvailableCount())
                    .skippedCanonicalNotResolved(diagnostics.getSkippedCanonicalNotResolved())
                    .skippedByRelevance(diagnostics.getSkippedByRelevance())
                    .duplicateSkipped(stats.duplicateCount())
                    .apiQuotaLeft(diagnostics.getApiQuotaLeft())
                    .apiQuotaUsed(diagnostics.getApiQuotaUsed())
                    .apiQuotaRequest(diagnostics.getApiQuotaRequest())
                    .apiReturnedCount(diagnostics.getApiReturnedCount())
                    .fullContentEligibleCount(diagnostics.getFullContentEligibleCount())
                    .skippedTooOld(diagnostics.getSkippedTooOld())
                    .skippedBlockedSource(diagnostics.getSkippedBlockedSource())
                    .existing(stats.existingCount())
                    .saved(stats.savedCount())
                    .firstReturnedTitle(diagnostics.getFirstReturnedTitle())
                    .firstReturnedPublishedAt(diagnostics.getFirstReturnedPublishedAt())
                    .startupSync(startupSync)
                    .errorMessage(diagnostics.getErrorMessage())
                    .lastErrors(diagnostics.getLastErrors())
                    .fetchedCount(items.size())
                    .validCount(stats.validCount())
                    .invalidCount(stats.invalidCount())
                    .duplicateCount(stats.duplicateCount())
                    .existingCount(stats.existingCount())
                    .savedCount(stats.savedCount())
                    .parseSuccessRatio(parseSuccessRatio)
                    .build();
            markSuccessfulSync(providerType, response);
            return response;
        } catch (Exception ex) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            LoggingContext.put(LoggingConstants.SUCCESS_KEY, Boolean.FALSE.toString());
            LoggingContext.put(LoggingConstants.DURATION_MS_KEY, String.valueOf(durationMs));
            logger.error(
                    "News provider sync failed. provider: {}, symbol: {}, startupSync: {}, schedulerSync: {}, requestedLimit: {}, effectiveLimit: {}, durationMs: {}, message: {}",
                    providerType.name(),
                    symbol,
                    startupSync,
                    schedulerSync,
                    requestedLimit,
                    effectiveLimit,
                    durationMs,
                    ex.getMessage(),
                    ex
            );
            ProviderSyncDiagnostics diagnostics = resolveProviderDiagnostics(provider, providerType);
            return NewsSyncResponseDto.builder()
                    .provider(providerType.name())
                    .enabled(diagnostics.isEnabled())
                    .feedUrlCount(diagnostics.getFeedUrlCount())
                    .fetched(0)
                    .fetchedFromFeed(diagnostics.getFetchedFromFeed())
                    .fetchedFromApi(diagnostics.getFetchedFromApi())
                    .canonicalResolved(diagnostics.getCanonicalResolved())
                    .canonicalFailed(diagnostics.getCanonicalFailed())
                    .extractedFullContent(diagnostics.getExtractedFullContent())
                    .skippedFullContentNotAvailable(diagnostics.getSkippedFullContentNotAvailable())
                    .skippedCanonicalNotResolved(diagnostics.getSkippedCanonicalNotResolved())
                    .skippedByRelevance(diagnostics.getSkippedByRelevance())
                    .duplicateSkipped(0)
                    .apiQuotaLeft(diagnostics.getApiQuotaLeft())
                    .apiQuotaUsed(diagnostics.getApiQuotaUsed())
                    .apiQuotaRequest(diagnostics.getApiQuotaRequest())
                    .apiReturnedCount(diagnostics.getApiReturnedCount())
                    .fullContentEligibleCount(diagnostics.getFullContentEligibleCount())
                    .skippedTooOld(diagnostics.getSkippedTooOld())
                    .skippedBlockedSource(diagnostics.getSkippedBlockedSource())
                    .existing(0)
                    .saved(0)
                    .firstReturnedTitle(diagnostics.getFirstReturnedTitle())
                    .firstReturnedPublishedAt(diagnostics.getFirstReturnedPublishedAt())
                    .startupSync(startupSync)
                    .errorMessage(hasText(diagnostics.getErrorMessage()) ? diagnostics.getErrorMessage() : ex.getMessage())
                    .lastErrors(diagnostics.getLastErrors())
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

    private int resolveEffectiveLimit(
            NewsProviderType providerType,
            boolean startupSync,
            boolean schedulerSync,
            Integer requestedLimit
    ) {
        if (startupSync) {
            return switch (providerType) {
                case CNBC_RSS -> 5;
                default -> 0;
            };
        }
        if (requestedLimit != null) {
            return Math.max(1, requestedLimit);
        }
        return 0;
    }

    private LocalDateTime getLastSuccessfulSyncAt(NewsProviderType providerType) {
        return newsProviderSyncStateRepository.findById(providerType.name())
                .map(NewsProviderSyncState::getLastSuccessfulSyncAt)
                .orElse(null);
    }

    private void markSuccessfulSync(NewsProviderType providerType, NewsSyncResponseDto response) {
        if (response == null || response.getErrorMessage() != null) {
            return;
        }
        NewsProviderSyncState state = newsProviderSyncStateRepository.findById(providerType.name())
                .orElseGet(NewsProviderSyncState::new);
        state.setProvider(providerType.name());
        state.setLastSuccessfulSyncAt(LocalDateTime.now());
        newsProviderSyncStateRepository.save(state);
    }

    private List<NewsItemDto> fetchItems(
            NewsProvider provider,
            NewsProviderType providerType,
            String symbol,
            int limit,
            LocalDateTime earliestPublishDate
    ) {
        if (hasText(symbol)) {
            return provider.fetchCompanyNews(symbol, limit);
        }
        return provider.fetchLatestNews(limit);
    }

    private ProviderSyncDiagnostics resolveProviderDiagnostics(NewsProvider provider, NewsProviderType providerType) {
        if (provider instanceof ProviderSyncDiagnosticsAware diagnosticsAware) {
            ProviderSyncDiagnostics diagnostics = diagnosticsAware.getLastDiagnostics();
            if (diagnostics != null) {
                return diagnostics;
            }
        }
        return ProviderSyncDiagnostics.builder()
                .provider(providerType.name())
                .enabled(true)
                .feedUrlCount(0)
                .fetched(0)
                .fetchedFromFeed(0)
                .fetchedFromApi(0)
                .canonicalResolved(0)
                .canonicalFailed(0)
                .extractedFullContent(0)
                .skippedFullContentNotAvailable(0)
                .skippedCanonicalNotResolved(0)
                .skippedByRelevance(0)
                .apiQuotaLeft(null)
                .apiQuotaUsed(null)
                .apiQuotaRequest(null)
                .errorMessage(null)
                .lastErrors(List.of())
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
        int invalidLengthCount = 0;
        int skippedFullContentNotAvailableCount = 0;
        List<News> toSave = new ArrayList<>();
        Map<String, News> existingNewsByExternalId = findExistingNewsByExternalId(items);
        Set<String> existingExternalIds = existingNewsByExternalId.keySet();
        Set<String> seenExternalIds = new HashSet<>();
        List<News> existingToUpdate = new ArrayList<>();
        FieldLengthStats fieldLengthStats = new FieldLengthStats();

        for (NewsItemDto item : items) {
            fieldLengthStats.observe(item);
            ValidationResult validationResult = validateForPersistence(item);
            if (!validationResult.valid()) {
                if (validationResult.invalidBecauseLength()) {
                    logOversizedItem(providerName, item);
                    invalidLengthCount++;
                } else if (validationResult.skippedFullContentNotAvailable()) {
                    skippedFullContentNotAvailableCount++;
                    logger.debug("Skipping news item without full content. provider: {}, qualityStatus: {}, title: {}",
                            providerName,
                            item != null ? item.getQualityStatus() : null,
                            truncateForLog(item != null ? item.getTitle() : null));
                } else {
                    logger.debug("Skipping invalid news item. externalId: {}", item != null ? item.getExternalId() : null);
                }
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
                    if (hasText(item.getSummary())) {
                        String newSummary = item.getSummary().trim();
                        int existingLen = existingNews.getSummary() != null ? existingNews.getSummary().trim().length() : 0;
                        if (existingLen == 0 || (newSummary.length() >= 500 && newSummary.length() > existingLen + 200)) {
                            existingNews.setSummary(newSummary);
                            needsUpdate = true;
                        }
                    }
                    if (existingNews.getPublishedAt() == null && item.getPublishedAt() != null) {
                        existingNews.setPublishedAt(item.getPublishedAt());
                        needsUpdate = true;
                    }
                    if (existingNews.getContentEnrichedAt() == null && item.getContentEnrichedAt() != null) {
                        existingNews.setContentEnrichedAt(item.getContentEnrichedAt());
                        needsUpdate = true;
                    }
                    if (!hasText(existingNews.getQualityStatus()) && hasText(item.getQualityStatus())) {
                        existingNews.setQualityStatus(item.getQualityStatus().trim());
                        needsUpdate = true;
                    } else if (isHigherQuality(item.getQualityStatus(), existingNews.getQualityStatus())) {
                        existingNews.setQualityStatus(item.getQualityStatus().trim());
                        needsUpdate = true;
                    }
                    if (!Boolean.TRUE.equals(existingNews.getIsKapDisclosure()) && Boolean.TRUE.equals(item.getIsKapDisclosure())) {
                        existingNews.setIsKapDisclosure(true);
                        needsUpdate = true;
                    }
                    if (!hasText(existingNews.getDisclosureType()) && hasText(item.getDisclosureType())) {
                        existingNews.setDisclosureType(item.getDisclosureType().trim());
                        needsUpdate = true;
                    }
                    if (!hasText(existingNews.getRelatedSymbol()) && hasText(item.getRelatedSymbol())) {
                        existingNews.setRelatedSymbol(item.getRelatedSymbol().trim());
                        needsUpdate = true;
                    }
                    if (!hasText(existingNews.getContentSections()) && hasText(item.getContentSections())) {
                        existingNews.setContentSections(item.getContentSections());
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
                    .contentEnrichedAt(item.getContentEnrichedAt())
                    .qualityStatus(item.getQualityStatus())
                    .isKapDisclosure(Boolean.TRUE.equals(item.getIsKapDisclosure()))
                    .disclosureType(item.getDisclosureType())
                    .contentSections(item.getContentSections())
                    .importanceScore(0)
                    .build());
        }

        logger.info(
                "News persistence field lengths. provider: {}, maxTitleLength: {}, maxSummaryLength: {}, maxUrlLength: {}, maxExternalIdLength: {}, maxImageUrlLength: {}",
                providerName,
                fieldLengthStats.maxTitleLength,
                fieldLengthStats.maxSummaryLength,
                fieldLengthStats.maxUrlLength,
                fieldLengthStats.maxExternalIdLength,
                fieldLengthStats.maxImageUrlLength
        );

        toSave.forEach(news -> news.setImportanceScore(newsImportanceScoringService.calculateScore(news)));

        if (!toSave.isEmpty()) {
            List<News> saved = persistNewItems(providerName, toSave);
            savedCount = saved.size();
            sendNotifications(saved);
        }
        if (!existingToUpdate.isEmpty()) {
            persistExistingUpdates(providerName, existingToUpdate);
        }

        int validCount = items.size() - invalidCount;
        logger.info(
                "News persistence completed. provider: {}, fetched: {}, valid: {}, invalid: {}, duplicate: {}, existing: {}, saved: {}, invalidBecauseMissingTitle: {}, invalidBecauseMissingUrl: {}, invalidBecauseMissingSource: {}, invalidBecauseMissingExternalId: {}, invalidBecauseMissingDate: {}, invalidBecauseLength: {}, skippedFullContentNotAvailable: {}",
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
                missingDateCount,
                invalidLengthCount,
                skippedFullContentNotAvailableCount
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
                missingDateCount,
                skippedFullContentNotAvailableCount
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
            return ValidationResult.invalid(null, true, true, true, true, false, false);
        }
        boolean missingExternalId = !hasText(item.getExternalId());
        boolean missingTitle = !hasText(item.getTitle());
        boolean missingSource = !hasText(item.getSource());
        boolean missingUrl = !hasText(item.getUrl());
        boolean missingProvider = !hasText(item.getProvider());
        boolean missingRegionScope = !hasText(item.getRegionScope());
        boolean skippedFullContentNotAvailable = !Boolean.TRUE.equals(item.getIsKapDisclosure())
                && !isFullContentPersistable(item);
        boolean invalidBecauseLength = exceedsLength(item.getTitle(), TITLE_MAX_LENGTH)
                || exceedsLength(item.getSource(), SOURCE_MAX_LENGTH)
                || exceedsLength(item.getProvider(), PROVIDER_MAX_LENGTH)
                || exceedsLength(item.getLanguage(), LANGUAGE_MAX_LENGTH)
                || exceedsLength(item.getRegionScope(), REGION_SCOPE_MAX_LENGTH)
                || exceedsLength(item.getCategory(), CATEGORY_MAX_LENGTH)
                || exceedsLength(item.getRelatedSymbol(), RELATED_SYMBOL_MAX_LENGTH)
                || exceedsLength(item.getQualityStatus(), QUALITY_STATUS_MAX_LENGTH)
                || exceedsLength(item.getDisclosureType(), DISCLOSURE_TYPE_MAX_LENGTH);

        return new ValidationResult(
                item,
                !(missingExternalId || missingTitle || missingSource || missingUrl || missingProvider || missingRegionScope || invalidBecauseLength || skippedFullContentNotAvailable),
                missingExternalId,
                missingTitle,
                missingUrl,
                missingSource,
                invalidBecauseLength,
                skippedFullContentNotAvailable
        );
    }

    private boolean isFullContentPersistable(NewsItemDto item) {
        if (item == null) {
            return false;
        }
        if (!"FULL_CONTENT".equalsIgnoreCase(item.getQualityStatus())) {
            return false;
        }
        return hasText(item.getSummary()) && item.getSummary().trim().length() >= 500;
    }

    private List<News> persistNewItems(String providerName, List<News> toSave) {
        try {
            return newsRepository.saveAll(toSave);
        } catch (DataIntegrityViolationException exception) {
            logger.warn("Batch news insert failed. provider: {}, itemCount: {}, reason: {}. Falling back to single-row persistence.",
                    providerName, toSave.size(), exception.getMostSpecificCause() != null ? exception.getMostSpecificCause().getMessage() : exception.getMessage());
            List<News> saved = new ArrayList<>();
            for (News news : toSave) {
                try {
                    saved.add(newsRepository.save(news));
                } catch (DataIntegrityViolationException rowException) {
                    logNewsEntityLengths(providerName, news, rowException);
                }
            }
            return saved;
        }
    }

    private void persistExistingUpdates(String providerName, List<News> existingToUpdate) {
        try {
            newsRepository.saveAll(existingToUpdate);
        } catch (DataIntegrityViolationException exception) {
            logger.warn("Batch news update failed. provider: {}, itemCount: {}, reason: {}. Falling back to single-row persistence.",
                    providerName, existingToUpdate.size(), exception.getMostSpecificCause() != null ? exception.getMostSpecificCause().getMessage() : exception.getMessage());
            for (News news : existingToUpdate) {
                try {
                    newsRepository.save(news);
                } catch (DataIntegrityViolationException rowException) {
                    logNewsEntityLengths(providerName, news, rowException);
                }
            }
        }
    }

    private void sendNotifications(List<News> saved) {
        if (!notificationProperties.isEnabled()) {
            return;
        }
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

    private void logOversizedItem(String providerName, NewsItemDto item) {
        logger.warn(
                "Skipping oversized news item before persistence. provider: {}, titleLength: {}, summaryLength: {}, urlLength: {}, externalIdLength: {}, imageUrlLength: {}, sourceLength: {}, providerLength: {}, languageLength: {}, regionScopeLength: {}, categoryLength: {}, relatedSymbolLength: {}, qualityStatusLength: {}, disclosureTypeLength: {}, title: {}, urlPreview: {}",
                providerName,
                safeLength(item != null ? item.getTitle() : null),
                safeLength(item != null ? item.getSummary() : null),
                safeLength(item != null ? item.getUrl() : null),
                safeLength(item != null ? item.getExternalId() : null),
                safeLength(item != null ? item.getImageUrl() : null),
                safeLength(item != null ? item.getSource() : null),
                safeLength(item != null ? item.getProvider() : null),
                safeLength(item != null ? item.getLanguage() : null),
                safeLength(item != null ? item.getRegionScope() : null),
                safeLength(item != null ? item.getCategory() : null),
                safeLength(item != null ? item.getRelatedSymbol() : null),
                safeLength(item != null ? item.getQualityStatus() : null),
                safeLength(item != null ? item.getDisclosureType() : null),
                truncateForLog(item != null ? item.getTitle() : null),
                truncateForLog(item != null ? item.getUrl() : null)
        );
    }

    private void logNewsEntityLengths(String providerName, News news, DataIntegrityViolationException exception) {
        logger.warn(
                "Skipping news row after persistence failure. provider: {}, reason: {}, titleLength: {}, summaryLength: {}, urlLength: {}, externalIdLength: {}, imageUrlLength: {}, title: {}, urlPreview: {}",
                providerName,
                exception.getMostSpecificCause() != null ? exception.getMostSpecificCause().getMessage() : exception.getMessage(),
                safeLength(news.getTitle()),
                safeLength(news.getSummary()),
                safeLength(news.getUrl()),
                safeLength(news.getExternalId()),
                safeLength(news.getImageUrl()),
                truncateForLog(news.getTitle()),
                truncateForLog(news.getUrl())
        );
    }

    private boolean exceedsLength(String value, int maxLength) {
        return hasText(value) && value.trim().length() > maxLength;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String truncateForLog(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 177) + "...";
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
                byKapDisclosure(request),
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

    private Specification<News> byKapDisclosure(NewsSearchRequest request) {
        if (request.getIsKapDisclosure() == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("isKapDisclosure"), request.getIsKapDisclosure());
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

    private boolean isHigherQuality(String candidate, String existing) {
        return qualityRank(candidate) > qualityRank(existing);
    }

    private int qualityRank(String value) {
        if (!hasText(value)) {
            return 0;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "FULL_CONTENT" -> 3;
            case "SUMMARY_ONLY" -> 2;
            case "SOURCE_LINK_ONLY" -> 1;
            default -> 0;
        };
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
        return newsPresentationMapper.toResponse(news);
    }

    private List<RelatedInstrumentDto> resolveRelatedInstruments(News news) {
        Map<String, RelatedInstrumentCandidate> matched = detectRelatedInstruments(news);
        boolean isKapWithSymbol = Boolean.TRUE.equals(news.getIsKapDisclosure()) && hasText(news.getRelatedSymbol());
        if (!isKapWithSymbol) {
            matched.putAll(detectThemeRelatedInstruments(news, matched));
        }
        if (matched.isEmpty()) {
            return List.of();
        }

        return matched.values().stream()
                .sorted(Comparator
                        .comparingInt((RelatedInstrumentCandidate candidate) -> -relationTypePriority(candidate.relationType()))
                        .thenComparingInt(candidate -> -confidencePriority(candidate.confidence()))
                        .thenComparing(RelatedInstrumentCandidate::symbol))
                .limit(MAX_RELATED_INSTRUMENTS)
                .map(candidate -> {
                    Optional<MarketQueryService.MarketSnapshot> snapshot =
                            marketQueryService.findBySymbol(candidate.symbol(), parseInstrumentType(candidate.instrumentType()));

                    return RelatedInstrumentDto.builder()
                            .symbol(candidate.symbol())
                            .name(snapshot.map(MarketQueryService.MarketSnapshot::displayName).filter(this::hasText).orElse(candidate.name()))
                            .instrumentType(snapshot.map(MarketQueryService.MarketSnapshot::instrumentType).orElse(candidate.instrumentType()))
                            .lastPrice(snapshot.map(MarketQueryService.MarketSnapshot::price).orElse(null))
                            .changePercent(snapshot.map(MarketQueryService.MarketSnapshot::changeRate).orElse(null))
                            .relationType(candidate.relationType())
                            .confidence(candidate.confidence())
                            .reason(candidate.reason())
                            .build();
                })
                .toList();
    }

    private List<RelatedNewsItemDto> resolveRelatedNews(News news, List<RelatedInstrumentDto> relatedInstruments) {
        if (!hasText(news.getCategory())) {
            return List.of();
        }

        LocalDateTime publishedAfter = (news.getPublishedAt() != null ? news.getPublishedAt() : LocalDateTime.now()).minusDays(7);
        Set<String> matchedSymbols = relatedInstruments.stream()
                .map(RelatedInstrumentDto::symbol)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        Set<String> currentTokens = tokenize(news);

        return newsRepository.findRecentCandidatesForRelatedNews(news.getId(), news.getCategory(), publishedAfter).stream()
                .map(candidate -> scoreRelatedNews(candidate, matchedSymbols, currentTokens))
                .sorted(Comparator
                        .comparingInt(ScoredRelatedNews::score).reversed()
                        .thenComparing(scored -> scored.news().getPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(scored -> scored.news().getImportanceScore(), Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(scored -> scored.score() > 0)
                .limit(MAX_RELATED_NEWS)
                .map(scored -> RelatedNewsItemDto.builder()
                        .id(scored.news().getId())
                        .title(scored.news().getTitle())
                        .sourceName(newsPresentationMapper.toResponse(scored.news()).getSourceName())
                        .category(scored.news().getCategory())
                        .publishedAt(scored.news().getPublishedAt())
                        .importanceScore(scored.news().getImportanceScore())
                        .build())
                .toList();
    }

    private ScoredRelatedNews scoreRelatedNews(News candidate, Set<String> matchedSymbols, Set<String> currentTokens) {
        int score = 20;
        String candidateSymbol = normalizeSymbolValue(candidate.getRelatedSymbol());
        if (hasText(candidateSymbol) && matchedSymbols.contains(candidateSymbol)) {
            score += 100;
        }

        Set<String> candidateSymbols = detectRelatedInstruments(candidate).keySet();
        long aliasOverlap = candidateSymbols.stream().filter(matchedSymbols::contains).count();
        if (aliasOverlap > 0) {
            score += (int) aliasOverlap * 60;
        }

        Set<String> candidateTokens = tokenize(candidate);
        long tokenOverlap = candidateTokens.stream().filter(currentTokens::contains).count();
        score += Math.min(40, (int) tokenOverlap * 4);

        if (candidate.getImportanceScore() != null) {
            score += Math.min(15, Math.max(0, candidate.getImportanceScore() / 10));
        }

        return new ScoredRelatedNews(candidate, score);
    }

    private Map<String, RelatedInstrumentCandidate> detectRelatedInstruments(News news) {
        Map<String, RelatedInstrumentCandidate> matched = new LinkedHashMap<>();

        // KAP disclosures with a direct relatedSymbol: skip heuristic text scan, use only the declared symbol
        if (Boolean.TRUE.equals(news.getIsKapDisclosure()) && hasText(news.getRelatedSymbol())) {
            String normalizedSymbol = normalizeSymbolValue(news.getRelatedSymbol());
            if (hasText(normalizedSymbol)) {
                if (BIST_INSTRUMENT_ALIASES.containsKey(normalizedSymbol)) {
                    InstrumentAlias alias = BIST_INSTRUMENT_ALIASES.get(normalizedSymbol);
                    matched.put(normalizedSymbol, RelatedInstrumentCandidate.direct(alias.symbol(), alias.name(), alias.instrumentType()));
                } else {
                    matched.put(normalizedSymbol, RelatedInstrumentCandidate.direct(normalizedSymbol, normalizedSymbol, InstrumentType.STOCK.name()));
                }
            }
            return matched;
        }

        // Normal heuristic scan for non-KAP news
        String combinedText = normalizeText(String.join(" ",
                safeText(news.getRelatedSymbol()),
                safeText(news.getTitle()),
                safeText(news.getSummary())));
        Set<String> tokens = tokenizeText(combinedText);

        for (InstrumentAlias alias : BIST_INSTRUMENT_ALIASES.values()) {
            if (matchesInstrumentAlias(combinedText, tokens, alias)) {
                matched.put(alias.symbol(), RelatedInstrumentCandidate.direct(alias.symbol(), alias.name(), alias.instrumentType()));
            }
        }

        String normalizedRelatedSymbol = normalizeSymbolValue(news.getRelatedSymbol());
        if (hasText(normalizedRelatedSymbol) && BIST_INSTRUMENT_ALIASES.containsKey(normalizedRelatedSymbol)) {
            InstrumentAlias alias = BIST_INSTRUMENT_ALIASES.get(normalizedRelatedSymbol);
            matched.putIfAbsent(normalizedRelatedSymbol, RelatedInstrumentCandidate.direct(alias.symbol(), alias.name(), alias.instrumentType()));
        }

        return matched;
    }

    private Map<String, RelatedInstrumentCandidate> detectThemeRelatedInstruments(
            News news,
            Map<String, RelatedInstrumentCandidate> directMatches
    ) {
        String combinedText = normalizeText(String.join(" ",
                safeText(news.getTitle()),
                safeText(news.getSummary()),
                safeText(news.getCategory())));
        Set<String> tokens = tokenizeText(combinedText);

        Map<String, RelatedInstrumentCandidate> themed = new LinkedHashMap<>();
        for (ThemeRule themeRule : THEME_RULES) {
            boolean themeMatched = themeRule.keywords().stream().anyMatch(keyword -> containsKeyword(combinedText, tokens, keyword));
            if (!themeMatched) {
                continue;
            }

            for (ThemeInstrument themeInstrument : themeRule.instruments()) {
                RelatedInstrumentCandidate existingDirect = directMatches.get(themeInstrument.symbol());
                if (existingDirect != null) {
                    continue;
                }

                themed.merge(
                        themeInstrument.symbol(),
                        RelatedInstrumentCandidate.theme(
                                themeInstrument.symbol(),
                                themeInstrument.name(),
                                themeInstrument.instrumentType(),
                                themeInstrument.confidence(),
                                themeRule.reason()
                        ),
                        this::mergeThemeCandidates
                );
            }
        }

        return themed;
    }

    private RelatedInstrumentCandidate mergeThemeCandidates(RelatedInstrumentCandidate left, RelatedInstrumentCandidate right) {
        if (confidencePriority(right.confidence()) > confidencePriority(left.confidence())) {
            return right;
        }
        return left;
    }

    private boolean matchesInstrumentAlias(String normalizedText, Set<String> tokens, InstrumentAlias alias) {
        if (containsKeyword(normalizedText, tokens, alias.symbol())) {
            return true;
        }

        for (String keyword : alias.keywords()) {
            if (containsKeyword(normalizedText, tokens, keyword)) {
                return true;
            }
        }

        return false;
    }

    private int relationTypePriority(String relationType) {
        return "DIRECT".equalsIgnoreCase(relationType) ? 2 : 1;
    }

    private int confidencePriority(String confidence) {
        return switch (String.valueOf(confidence).toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private InstrumentType parseInstrumentType(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return InstrumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Set<String> tokenize(News news) {
        return tokenizeText(normalizeText(String.join(" ",
                safeText(news.getTitle()),
                safeText(news.getSummary()),
                safeText(news.getCategory()))));
    }

    private Set<String> tokenizeText(String normalizedText) {
        return TOKEN_SPLIT_PATTERN.splitAsStream(normalizedText)
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toSet());
    }

    private boolean containsKeyword(String normalizedText, Set<String> tokens, String keyword) {
        if (!hasText(keyword)) {
            return false;
        }
        String normalizedKeyword = normalizeText(keyword);
        if (normalizedKeyword.contains(" ")) {
            return normalizedText.contains(normalizedKeyword);
        }
        return tokens.contains(normalizedKeyword);
    }

    private String normalizeText(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT)
                .replace('İ', 'I')
                .replace('I', 'I')
                .replace('Ş', 'S')
                .replace('Ğ', 'G')
                .replace('Ü', 'U')
                .replace('Ö', 'O')
                .replace('Ç', 'C')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeSymbolValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, InstrumentAlias> createInstrumentAliases() {
        Map<String, InstrumentAlias> aliases = new LinkedHashMap<>();
        aliases.put("THYAO", new InstrumentAlias("THYAO", "Türk Hava Yolları", InstrumentType.STOCK.name(), Set.of("THYAO", "TURK HAVA YOLLARI", "THY", "TURKISH AIRLINES")));
        aliases.put("ASELS", new InstrumentAlias("ASELS", "Aselsan", InstrumentType.STOCK.name(), Set.of("ASELS", "ASELSAN")));
        aliases.put("AKBNK", new InstrumentAlias("AKBNK", "Akbank", InstrumentType.STOCK.name(), Set.of("AKBNK", "AKBANK")));
        aliases.put("BIMAS", new InstrumentAlias("BIMAS", "BİM", InstrumentType.STOCK.name(), Set.of("BIMAS", "BIM", "BIM BIRLESIK", "BIRLESIK MAGAZALAR")));
        aliases.put("KCHOL", new InstrumentAlias("KCHOL", "Koç Holding", InstrumentType.STOCK.name(), Set.of("KCHOL", "KOC HOLDING", "KOC")));
        aliases.put("TUPRS", new InstrumentAlias("TUPRS", "Tüpraş", InstrumentType.STOCK.name(), Set.of("TUPRS", "TUPRAS")));
        aliases.put("GARAN", new InstrumentAlias("GARAN", "Garanti BBVA", InstrumentType.STOCK.name(), Set.of("GARAN", "GARANTI", "GARANTI BBVA")));
        aliases.put("ISCTR", new InstrumentAlias("ISCTR", "İş Bankası", InstrumentType.STOCK.name(), Set.of("ISCTR", "IS BANKASI", "TURKIYE IS BANKASI", "ISBANK")));
        aliases.put("YKBNK", new InstrumentAlias("YKBNK", "Yapı Kredi", InstrumentType.STOCK.name(), Set.of("YKBNK", "YAPI KREDI")));
        aliases.put("EREGL", new InstrumentAlias("EREGL", "Ereğli Demir Çelik", InstrumentType.STOCK.name(), Set.of("EREGL", "EREGLI", "ERDEMIR", "EREGLI DEMIR CELIK")));
        aliases.put("SISE", new InstrumentAlias("SISE", "Şişecam", InstrumentType.STOCK.name(), Set.of("SISE", "SISECAM", "TURKIYE SISE VE CAM")));
        aliases.put("FROTO", new InstrumentAlias("FROTO", "Ford Otosan", InstrumentType.STOCK.name(), Set.of("FROTO", "FORD OTOSAN", "FORD")));
        aliases.put("TOASO", new InstrumentAlias("TOASO", "Tofaş", InstrumentType.STOCK.name(), Set.of("TOASO", "TOFAS")));
        aliases.put("MGROS", new InstrumentAlias("MGROS", "Migros", InstrumentType.STOCK.name(), Set.of("MGROS", "MIGROS")));
        aliases.put("KRDMD", new InstrumentAlias("KRDMD", "Kardemir", InstrumentType.STOCK.name(), Set.of("KRDMD", "KARDEMIR")));
        aliases.put("AKSEN", new InstrumentAlias("AKSEN", "Aksa Enerji", InstrumentType.STOCK.name(), Set.of("AKSEN", "AKSA ENERJI")));
        aliases.put("ENJSA", new InstrumentAlias("ENJSA", "Enerjisa", InstrumentType.STOCK.name(), Set.of("ENJSA", "ENERJISA")));
        aliases.put("CIMSA", new InstrumentAlias("CIMSA", "Çimsa", InstrumentType.STOCK.name(), Set.of("CIMSA")));
        aliases.put("OYAKC", new InstrumentAlias("OYAKC", "OYAK Çimento", InstrumentType.STOCK.name(), Set.of("OYAKC", "OYAK CIMENTO")));
        aliases.put("SAHOL", new InstrumentAlias("SAHOL", "Sabancı Holding", InstrumentType.STOCK.name(), Set.of("SAHOL", "SABANCI HOLDING", "SABANCI")));
        aliases.put("XU100", new InstrumentAlias("XU100", "BIST 100", InstrumentType.INDEX.name(), Set.of("XU100", "BIST100", "BIST 100")));
        return aliases;
    }

    private static List<ThemeRule> createThemeRules() {
        return List.of(
                new ThemeRule(Set.of("KARBON", "EMISYON", "IKLIM", "SURDURULEBILIRLIK", "YESIL DONUSUM", "KARBON FIYATLANDIRMASI"),
                        "Karbon fiyatlandirmasi / emisyon maliyeti temasi",
                        List.of(
                                new ThemeInstrument("EREGL", "Ereğli Demir Çelik", InstrumentType.STOCK.name(), "MEDIUM"),
                                new ThemeInstrument("KRDMD", "Kardemir", InstrumentType.STOCK.name(), "MEDIUM"),
                                new ThemeInstrument("TUPRS", "Tüpraş", InstrumentType.STOCK.name(), "MEDIUM"),
                                new ThemeInstrument("AKSEN", "Aksa Enerji", InstrumentType.STOCK.name(), "LOW"),
                                new ThemeInstrument("ENJSA", "Enerjisa", InstrumentType.STOCK.name(), "LOW"),
                                new ThemeInstrument("CIMSA", "Çimsa", InstrumentType.STOCK.name(), "LOW"),
                                new ThemeInstrument("OYAKC", "OYAK Çimento", InstrumentType.STOCK.name(), "LOW")
                        )),
                new ThemeRule(Set.of("BUYUME", "RESESYON", "KURESEL EKONOMI", "TICARET", "TEDARIK ZINCIRI"),
                        "Kuresel buyume ve piyasa geneli etkisi",
                        List.of(
                                new ThemeInstrument("XU100", "BIST 100", InstrumentType.INDEX.name(), "LOW"),
                                new ThemeInstrument("THYAO", "Türk Hava Yolları", InstrumentType.STOCK.name(), "LOW"),
                                new ThemeInstrument("TUPRS", "Tüpraş", InstrumentType.STOCK.name(), "LOW"),
                                new ThemeInstrument("KCHOL", "Koç Holding", InstrumentType.STOCK.name(), "LOW"),
                                new ThemeInstrument("SAHOL", "Sabancı Holding", InstrumentType.STOCK.name(), "LOW")
                        )),
                new ThemeRule(Set.of("FAIZ", "TCMB", "ENFLASYON", "KREDI", "POLITIKA FAIZI"),
                        "Faiz ve kredi hassasiyeti",
                        List.of(
                                new ThemeInstrument("AKBNK", "Akbank", InstrumentType.STOCK.name(), "MEDIUM"),
                                new ThemeInstrument("GARAN", "Garanti BBVA", InstrumentType.STOCK.name(), "MEDIUM"),
                                new ThemeInstrument("ISCTR", "İş Bankası", InstrumentType.STOCK.name(), "MEDIUM"),
                                new ThemeInstrument("YKBNK", "Yapı Kredi", InstrumentType.STOCK.name(), "MEDIUM")
                        )),
                new ThemeRule(Set.of("PETROL", "BRENT", "AKARYAKIT", "YAKIT"),
                        "Petrol/yakit maliyeti temasi",
                        List.of(
                                new ThemeInstrument("TUPRS", "Tüpraş", InstrumentType.STOCK.name(), "MEDIUM"),
                                new ThemeInstrument("THYAO", "Türk Hava Yolları", InstrumentType.STOCK.name(), "MEDIUM")
                        )),
                new ThemeRule(Set.of("SAVUNMA", "SAVAS", "JEOPOLITIK", "GUVENLIK"),
                        "Savunma ve jeopolitik tema",
                        List.of(new ThemeInstrument("ASELS", "Aselsan", InstrumentType.STOCK.name(), "MEDIUM"))),
                new ThemeRule(Set.of("GIDA", "PERAKENDE", "TUKETIM"),
                        "Tuketim ve perakende temasi",
                        List.of(
                                new ThemeInstrument("BIMAS", "BİM", InstrumentType.STOCK.name(), "LOW"),
                                new ThemeInstrument("MGROS", "Migros", InstrumentType.STOCK.name(), "LOW")
                        )),
                new ThemeRule(Set.of("TURIZM", "HAVACILIK", "YOLCU"),
                        "Havacilik/turizm talebi temasi",
                        List.of(new ThemeInstrument("THYAO", "Türk Hava Yolları", InstrumentType.STOCK.name(), "MEDIUM")))
        );
    }

    private record QueryContext(NewsScope scope, NewsProviderType provider) {
    }

    private record InstrumentAlias(String symbol, String name, String instrumentType, Set<String> keywords) {
    }

    private record ThemeRule(Set<String> keywords, String reason, List<ThemeInstrument> instruments) {
    }

    private record ThemeInstrument(String symbol, String name, String instrumentType, String confidence) {
    }

    private record RelatedInstrumentCandidate(
            String symbol,
            String name,
            String instrumentType,
            String relationType,
            String confidence,
            String reason
    ) {
        private static RelatedInstrumentCandidate direct(String symbol, String name, String instrumentType) {
            return new RelatedInstrumentCandidate(symbol, name, instrumentType, "DIRECT", "HIGH", "Sirket/sembol eslesmesi");
        }

        private static RelatedInstrumentCandidate theme(String symbol, String name, String instrumentType, String confidence, String reason) {
            return new RelatedInstrumentCandidate(symbol, name, instrumentType, "THEME", confidence, reason);
        }
    }

    private record ScoredRelatedNews(News news, int score) {
    }

    private record ValidationResult(
            NewsItemDto item,
            boolean valid,
            boolean missingExternalId,
            boolean missingTitle,
            boolean missingUrl,
            boolean missingSource,
            boolean invalidBecauseLength,
            boolean skippedFullContentNotAvailable
    ) {
        private static ValidationResult invalid(
                NewsItemDto item,
                boolean missingExternalId,
                boolean missingTitle,
                boolean missingUrl,
                boolean missingSource,
                boolean invalidBecauseLength,
                boolean skippedFullContentNotAvailable
        ) {
            return new ValidationResult(item, false, missingExternalId, missingTitle, missingUrl, missingSource, invalidBecauseLength, skippedFullContentNotAvailable);
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
            int missingDateCount,
            int skippedFullContentNotAvailableCount
    ) {
        private static PersistenceStats empty() {
            return new PersistenceStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static final class FieldLengthStats {
        private int maxTitleLength;
        private int maxSummaryLength;
        private int maxUrlLength;
        private int maxExternalIdLength;
        private int maxImageUrlLength;

        private void observe(NewsItemDto item) {
            if (item == null) {
                return;
            }
            maxTitleLength = Math.max(maxTitleLength, lengthOf(item.getTitle()));
            maxSummaryLength = Math.max(maxSummaryLength, lengthOf(item.getSummary()));
            maxUrlLength = Math.max(maxUrlLength, lengthOf(item.getUrl()));
            maxExternalIdLength = Math.max(maxExternalIdLength, lengthOf(item.getExternalId()));
            maxImageUrlLength = Math.max(maxImageUrlLength, lengthOf(item.getImageUrl()));
        }

        private int lengthOf(String value) {
            return value == null ? 0 : value.length();
        }
    }
}
