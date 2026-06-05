package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import com.emrehalli.financeportal.news.config.NewsNotificationProperties;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsCategoryRepairResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsFavoriteResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.dto.response.NewsImportanceRecalculationResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsPurgeResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncProviderResultDto;
import com.emrehalli.financeportal.news.dto.response.RelatedNewsItemDto;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.entity.NewsFavorite;
import com.emrehalli.financeportal.news.entity.NewsProviderSyncState;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsScope;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnosticsAware;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import com.emrehalli.financeportal.news.repository.NewsFavoriteRepository;
import com.emrehalli.financeportal.news.repository.NewsProviderSyncStateRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import com.emrehalli.financeportal.user.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
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
    private static final int MIN_SUMMARY_LENGTH = 80;
    private static final int DUPLICATE_LOOKBACK_DAYS = 3;
    private static final int MIN_FINANCIAL_RELEVANCE_SCORE = 2;
    private static final int MAX_RELATED_NEWS = 4;
    private static final int MAX_CATEGORY_REPAIR_LIMIT = 5_000;
    private static final int CATEGORY_REPAIR_SAMPLE_LIMIT = 10;
    private static final Set<String> TRACKING_QUERY_PARAMS = Set.of(
            "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "ref_src", "spm", "igshid"
    );
    private static final List<String> FINANCIAL_RELEVANCE_KEYWORDS = List.of(
            "borsa", "bist", "stock", "stocks", "share", "shares", "equity", "finance", "financial",
            "economy", "economic", "market", "markets", "faiz", "interest", "rate", "rates", "tcmb",
            "fed", "bond", "tahvil", "forex", "fx", "doviz", "currency", "kur", "gold", "altin",
            "oil", "petrol", "commodity", "emtia", "crypto", "kripto", "bitcoin", "ethereum",
            "earnings", "bilanco", "revenue", "profit", "kar", "zarar", "disclosure", "kap"
    );

    private final NewsRepository newsRepository;
    private final NewsFavoriteRepository newsFavoriteRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NewsProviderSyncStateRepository newsProviderSyncStateRepository;
    private final Map<String, NewsProvider> providerMap;
    private final NewsImportanceScoringService newsImportanceScoringService;
    private final NotificationService notificationService;
    private final NewsNotificationProperties notificationProperties;
    private final NewsPresentationMapper newsPresentationMapper;
    private final NewsCategoryClassifier newsCategoryClassifier;
    private final FinancialImpactClassifier financialImpactClassifier;

    @Autowired
    public NewsService(
            NewsRepository newsRepository,
            NewsFavoriteRepository newsFavoriteRepository,
            UserRepository userRepository,
            UserService userService,
            NewsProviderSyncStateRepository newsProviderSyncStateRepository,
            List<NewsProvider> providers,
            NewsImportanceScoringService newsImportanceScoringService,
            NotificationService notificationService,
            NewsNotificationProperties notificationProperties,
            NewsPresentationMapper newsPresentationMapper,
            NewsCategoryClassifier newsCategoryClassifier,
            FinancialImpactClassifier financialImpactClassifier
    ) {
        this.newsRepository = newsRepository;
        this.newsFavoriteRepository = newsFavoriteRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.newsProviderSyncStateRepository = newsProviderSyncStateRepository;
        this.newsImportanceScoringService = newsImportanceScoringService;
        this.notificationService = notificationService;
        this.notificationProperties = notificationProperties;
        this.newsPresentationMapper = newsPresentationMapper;
        this.newsCategoryClassifier = newsCategoryClassifier;
        this.financialImpactClassifier = financialImpactClassifier;
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
        Long favoriteUserId = resolveFavoriteFilterUserId(request);
        QueryContext context = resolveQueryContext(request);
        String resolvedSortBy = resolveSortBy(sortBy);
        Sort.Direction resolvedSortDirection = resolveSortDirection(sortDirection);
        Pageable pageable = PageRequest.of(
                page,
                size,
                resolvePageableSort(resolvedSortBy, resolvedSortDirection)
        );
        Specification<News> specification = buildSpecification(request, context, resolvedSortBy, resolvedSortDirection, favoriteUserId);

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

        List<RelatedNewsItemDto> relatedNews;
        try {
            relatedNews = resolveRelatedNews(news);
        } catch (Exception exception) {
            logger.warn("Failed to resolve related news. newsId: {}, reason: {}", id, exception.getMessage());
            relatedNews = List.of();
        }

        return NewsRelatedResponseDto.builder()
                .relatedNews(relatedNews)
                .build();
    }
    @Transactional
    public NewsFavoriteResponseDto addFavoriteForCurrentUser(Long newsId) {
        User user = userService.getCurrentAuthenticatedUserEntity();
        return addFavorite(user.getId(), newsId);
    }

    @Transactional
    public NewsFavoriteResponseDto addFavorite(Long userId, Long newsId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + newsId));

        Optional<NewsFavorite> existing = newsFavoriteRepository.findByUserIdAndNewsId(userId, newsId);
        if (existing.isPresent()) {
            return toFavoriteResponse(existing.get());
        }

        NewsFavorite favorite = NewsFavorite.builder()
                .user(user)
                .news(news)
                .createdAt(LocalDateTime.now())
                .build();

        return toFavoriteResponse(newsFavoriteRepository.save(favorite));
    }

    @Transactional
    public void removeFavoriteForCurrentUser(Long newsId) {
        User user = userService.getCurrentAuthenticatedUserEntity();
        removeFavorite(user.getId(), newsId);
    }

    @Transactional
    public void removeFavorite(Long userId, Long newsId) {
        if (!newsFavoriteRepository.existsByUserIdAndNewsId(userId, newsId)) {
            return;
        }
        newsFavoriteRepository.deleteByUserIdAndNewsId(userId, newsId);
    }

    @Transactional
    public List<NewsFavoriteResponseDto> getCurrentUserFavorites() {
        User user = userService.getCurrentAuthenticatedUserEntity();
        return getUserFavorites(user.getId());
    }

    @Transactional(readOnly = true)
    public List<NewsFavoriteResponseDto> getUserFavorites(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return newsFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toFavoriteResponse)
                .toList();
    }

    private NewsFavoriteResponseDto toFavoriteResponse(NewsFavorite favorite) {
        return NewsFavoriteResponseDto.builder()
                .id(favorite.getId())
                .userId(favorite.getUser().getId())
                .newsId(favorite.getNews().getId())
                .createdAt(favorite.getCreatedAt())
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
    public NewsCategoryRepairResponseDto repairCategories(int limit, boolean dryRun) {
        int validatedLimit = Math.max(1, Math.min(limit, MAX_CATEGORY_REPAIR_LIMIT));
        List<News> candidates = newsRepository.findRecentNormalNews(
                PageRequest.of(0, validatedLimit, Sort.by(Sort.Direction.DESC, "publishedAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")))
        );

        int processedCount = 0;
        int changedCategoryCount = 0;
        int unchangedCount = 0;
        int skippedKapCount = 0;
        List<NewsCategoryRepairResponseDto.SampleChangeDto> sampleChanges = new ArrayList<>();

        for (News news : candidates) {
            processedCount++;
            if (isKapNews(news)) {
                skippedKapCount++;
                continue;
            }

            NewsCategoryClassifier.ClassificationResult classificationResult = newsCategoryClassifier.classify(
                    news.getTitle(),
                    news.getSummary(),
                    deriveClassificationPreview(news),
                    resolveRepairCategoryHint(news)
            );

            String oldCategory = normalizeNullable(news.getCategory());
            String newCategory;
            String repairReason = null;
            if (classificationResult.rejected() || !hasText(classificationResult.category())) {
                if (!isStaleCategoryRepairFallback(oldCategory)) {
                    unchangedCount++;
                    continue;
                }
                newCategory = NewsCategoryClassifier.GENERAL_ECONOMY;
                repairReason = "Current classifier rejected stale " + oldCategory + " category"
                        + (hasText(classificationResult.rejectReason()) ? " with " + classificationResult.rejectReason() : "")
                        + "; old stored category was " + oldCategory + ".";
            } else {
                newCategory = classificationResult.category().trim();
            }

            if (sameCategory(oldCategory, newCategory)) {
                unchangedCount++;
                continue;
            }

            changedCategoryCount++;
            addCategoryRepairSample(sampleChanges, news, oldCategory, newCategory, repairReason);
            if (!dryRun) {
                news.setCategory(newCategory);
            }
        }

        logger.info(
                "News category repair completed. dryRun={}, processedCount={}, changedCategoryCount={}, unchangedCount={}, skippedKapCount={}",
                dryRun,
                processedCount,
                changedCategoryCount,
                unchangedCount,
                skippedKapCount
        );

        return NewsCategoryRepairResponseDto.builder()
                .processedCount(processedCount)
                .changedCategoryCount(changedCategoryCount)
                .unchangedCount(unchangedCount)
                .skippedKapCount(skippedKapCount)
                .sampleChanges(sampleChanges)
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
        int timeoutCount = 0;
        int parseErrorCount = 0;
        List<NewsSyncProviderResultDto> providerResults = new ArrayList<>();

        for (NewsProviderType providerType : providers) {
            NewsSyncResponseDto result = syncSingleProvider(providerType, symbol, false, false, null);
            fetched += result.getFetchedCount();
            valid += result.getValidCount();
            invalid += result.getInvalidCount();
            duplicate += result.getDuplicateCount();
            existing += result.getExistingCount();
            saved += result.getSavedCount();
            timeoutCount += result.getTimeoutCount() != null ? result.getTimeoutCount() : 0;
            parseErrorCount += result.getParseErrorCount() != null ? result.getParseErrorCount() : 0;
            if (result.getProviderResults() != null) {
                providerResults.addAll(result.getProviderResults());
            }
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
                  .timeoutCount(timeoutCount)
                  .parseErrorCount(parseErrorCount)
                  .providerResults(providerResults)
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
            logger.info(
                    "News ingest structured. provider={}, fetchedCount={}, savedCount={}, duplicateCount={}, rejectReason={}, durationMs={}",
                    providerType.name(),
                    items.size(),
                    stats.savedCount(),
                    stats.duplicateCount(),
                    stats.rejectReasonSummary(),
                    durationMs
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
                    .timeoutCount(diagnostics.getTimeoutCount())
                    .parseErrorCount(diagnostics.getParseErrorCount())
                    .providerResults(List.of(buildProviderResult(
                            providerType.name(),
                            diagnostics.getErrorMessage() == null,
                            items.size(),
                            stats.savedCount(),
                            stats.invalidCount(),
                            stats.duplicateCount(),
                            diagnostics.getTimeoutCount(),
                            diagnostics.getParseErrorCount(),
                            diagnostics.getErrorMessage()
                    )))
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
                    .timeoutCount(diagnostics.getTimeoutCount() != null ? diagnostics.getTimeoutCount() : classifyTimeoutCount(ex))
                    .parseErrorCount(diagnostics.getParseErrorCount() != null ? diagnostics.getParseErrorCount() : classifyParseErrorCount(ex))
                    .providerResults(List.of(buildProviderResult(
                            providerType.name(),
                            false,
                            0,
                            0,
                            0,
                            0,
                            diagnostics.getTimeoutCount() != null ? diagnostics.getTimeoutCount() : classifyTimeoutCount(ex),
                            diagnostics.getParseErrorCount() != null ? diagnostics.getParseErrorCount() : classifyParseErrorCount(ex),
                            hasText(diagnostics.getErrorMessage()) ? diagnostics.getErrorMessage() : ex.getMessage()
                    )))
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
                .timeoutCount(0)
                .parseErrorCount(0)
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
        int lowRelevanceCount = 0;
        int duplicateByUrlCount = 0;
        int duplicateByTitleSourceCount = 0;
        List<News> toSave = new ArrayList<>();
        List<NewsItemDto> sanitizedItems = items.stream()
                .map(this::sanitizeIncomingItem)
                .toList();
        ExistingNewsLookup existingLookup = findExistingNews(sanitizedItems);
        Set<String> existingExternalIds = existingLookup.byExternalId().keySet();
        Set<String> seenExternalIds = new HashSet<>();
        Set<String> seenNormalizedUrls = new HashSet<>();
        Set<String> seenTitleSourceKeys = new HashSet<>();
        List<News> existingToUpdate = new ArrayList<>();
        FieldLengthStats fieldLengthStats = new FieldLengthStats();
        Map<String, Integer> rejectReasonCounts = new TreeMap<>();

        for (NewsItemDto item : sanitizedItems) {
            fieldLengthStats.observe(item);
            ValidationResult validationResult = validateForPersistence(item);
            if (!validationResult.valid()) {
                rejectReasonCounts.merge(validationResult.rejectReason(), 1, Integer::sum);
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
                    logger.debug(
                            "Skipping invalid news item. provider: {}, externalId: {}, rejectReason: {}, title: {}",
                            providerName,
                            item != null ? item.getExternalId() : null,
                            validationResult.rejectReason(),
                            truncateForLog(item != null ? item.getTitle() : null)
                    );
                }
                invalidCount++;
                missingExternalIdCount += validationResult.missingExternalId() ? 1 : 0;
                missingTitleCount += validationResult.missingTitle() ? 1 : 0;
                missingUrlCount += validationResult.missingUrl() ? 1 : 0;
                missingSourceCount += validationResult.missingSource() ? 1 : 0;
                missingDateCount += validationResult.missingDate() ? 1 : 0;
                lowRelevanceCount += validationResult.lowRelevance() ? 1 : 0;
                continue;
            }

            String externalId = item.getExternalId().trim();
            if (!seenExternalIds.add(externalId)) {
                logger.debug("Skipping duplicate news item within the same batch. externalId: {}", externalId);
                duplicateCount++;
                rejectReasonCounts.merge("DUPLICATE_EXTERNAL_ID_BATCH", 1, Integer::sum);
                continue;
            }

            String normalizedUrl = normalizeCanonicalUrl(item.getUrl());
            if (hasText(normalizedUrl) && !seenNormalizedUrls.add(normalizedUrl)) {
                duplicateCount++;
                duplicateByUrlCount++;
                rejectReasonCounts.merge("DUPLICATE_URL_BATCH", 1, Integer::sum);
                continue;
            }

            String titleSourceKey = buildTitleSourceKey(item.getTitle(), item.getSource());
            if (hasText(titleSourceKey) && !seenTitleSourceKeys.add(titleSourceKey)) {
                duplicateCount++;
                duplicateByTitleSourceCount++;
                rejectReasonCounts.merge("DUPLICATE_TITLE_SOURCE_BATCH", 1, Integer::sum);
                continue;
            }

            if (existingExternalIds.contains(externalId)) {
                existingCount++;
                News existingNews = existingLookup.byExternalId().get(externalId);
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
                    if (shouldUpdateCategory(existingNews, item)) {
                        existingNews.setCategory(item.getCategory().trim());
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

            if (hasText(normalizedUrl) && existingLookup.byNormalizedUrl().containsKey(normalizedUrl)) {
                existingCount++;
                duplicateCount++;
                duplicateByUrlCount++;
                rejectReasonCounts.merge("DUPLICATE_URL_EXISTING", 1, Integer::sum);
                continue;
            }

            if (hasText(titleSourceKey) && existingLookup.byTitleSource().containsKey(titleSourceKey)) {
                existingCount++;
                duplicateCount++;
                duplicateByTitleSourceCount++;
                rejectReasonCounts.merge("DUPLICATE_TITLE_SOURCE_EXISTING", 1, Integer::sum);
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

        int validCount = sanitizedItems.size() - invalidCount;
        logger.info(
                "News persistence completed. provider: {}, fetched: {}, valid: {}, invalid: {}, duplicate: {}, existing: {}, saved: {}, invalidBecauseMissingTitle: {}, invalidBecauseMissingUrl: {}, invalidBecauseMissingSource: {}, invalidBecauseMissingExternalId: {}, invalidBecauseMissingDate: {}, invalidBecauseLength: {}, skippedFullContentNotAvailable: {}, lowRelevance: {}, duplicateByUrl: {}, duplicateByTitleSource: {}",
                providerName,
                sanitizedItems.size(),
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
                skippedFullContentNotAvailableCount,
                lowRelevanceCount,
                duplicateByUrlCount,
                duplicateByTitleSourceCount
        );
        logger.info(
                "News ingest outcome. provider={}, fetchedCount={}, savedCount={}, duplicateCount={}, rejectReason={}",
                providerName,
                sanitizedItems.size(),
                savedCount,
                duplicateCount,
                rejectReasonCounts
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
                skippedFullContentNotAvailableCount,
                rejectReasonCounts.toString()
        );
    }

    private ExistingNewsLookup findExistingNews(List<NewsItemDto> items) {
        Set<String> externalIds = new HashSet<>();
        Set<String> normalizedUrls = new HashSet<>();
        Set<String> normalizedSources = new HashSet<>();
        Set<String> normalizedTitles = new HashSet<>();
        LocalDateTime publishedAfter = LocalDateTime.now().minusDays(DUPLICATE_LOOKBACK_DAYS);

        for (NewsItemDto item : items) {
            if (item == null) {
                continue;
            }
            if (hasText(item.getExternalId())) {
                externalIds.add(item.getExternalId().trim());
            }
            String normalizedUrl = normalizeCanonicalUrl(item.getUrl());
            if (hasText(normalizedUrl)) {
                normalizedUrls.add(normalizedUrl);
            }
            String titleKey = normalizeLookupValue(item.getTitle());
            String sourceKey = normalizeLookupValue(item.getSource());
            if (hasText(titleKey) && hasText(sourceKey)) {
                normalizedTitles.add(titleKey);
                normalizedSources.add(sourceKey);
            }
            if (item.getPublishedAt() != null) {
                LocalDateTime candidate = item.getPublishedAt().minusDays(DUPLICATE_LOOKBACK_DAYS);
                if (candidate.isBefore(publishedAfter)) {
                    publishedAfter = candidate;
                }
            }
        }

        Map<String, News> byExternalId = externalIds.isEmpty()
                ? Map.of()
                : newsRepository.findByExternalIdIn(externalIds).stream()
                .collect(Collectors.toMap(News::getExternalId, news -> news, (left, right) -> left));
        Map<String, News> byNormalizedUrl = normalizedUrls.isEmpty()
                ? Map.of()
                : newsRepository.findByUrlIn(normalizedUrls).stream()
                .collect(Collectors.toMap(news -> normalizeCanonicalUrl(news.getUrl()), news -> news, (left, right) -> left));
        Map<String, News> byTitleSource = (normalizedSources.isEmpty() || normalizedTitles.isEmpty())
                ? Map.of()
                : newsRepository.findRecentPotentialDuplicates(normalizedSources, normalizedTitles, publishedAfter).stream()
                .collect(Collectors.toMap(
                        news -> buildTitleSourceKey(news.getTitle(), news.getSource()),
                        news -> news,
                        (left, right) -> left
                ));
        return new ExistingNewsLookup(byExternalId, byNormalizedUrl, byTitleSource);
    }

    private NewsItemDto sanitizeIncomingItem(NewsItemDto item) {
        if (item == null) {
            return null;
        }
        boolean isKapDisclosure = Boolean.TRUE.equals(item.getIsKapDisclosure())
                || NewsProviderType.KAP.name().equalsIgnoreCase(item.getProvider());
        LocalDateTime resolvedPublishedAt = item.getPublishedAt() != null
                ? item.getPublishedAt()
                : item.getContentEnrichedAt() != null ? item.getContentEnrichedAt() : LocalDateTime.now();
        String normalizedCategory = hasText(item.getCategory()) ? item.getCategory().trim() : item.getCategory();
        String classificationRejectReason = null;
        if (!isKapDisclosure) {
            NewsCategoryClassifier.ClassificationResult classificationResult = newsCategoryClassifier.classify(
                    item.getTitle(),
                    item.getSummary(),
                    deriveClassificationPreview(item),
                    item.getCategory()
            );
            if (classificationResult.rejected()) {
                classificationRejectReason = classificationResult.rejectReason();
            } else {
                normalizedCategory = classificationResult.category();
            }
        }
        if (!hasText(normalizedCategory)) {
            normalizedCategory = isKapDisclosure ? "DISCLOSURE" : NewsCategoryClassifier.GENERAL_ECONOMY;
        }
        return NewsItemDto.builder()
                .externalId(hasText(item.getExternalId()) ? item.getExternalId().trim() : item.getExternalId())
                .title(hasText(item.getTitle()) ? item.getTitle().trim() : item.getTitle())
                .summary(hasText(item.getSummary()) ? item.getSummary().trim() : item.getSummary())
                .source(hasText(item.getSource()) ? item.getSource().trim() : item.getSource())
                .provider(hasText(item.getProvider()) ? item.getProvider().trim() : item.getProvider())
                .language(hasText(item.getLanguage()) ? item.getLanguage().trim() : item.getLanguage())
                .regionScope(hasText(item.getRegionScope()) ? item.getRegionScope().trim() : item.getRegionScope())
                .category(normalizedCategory)
                .relatedSymbol(hasText(item.getRelatedSymbol()) ? item.getRelatedSymbol().trim() : item.getRelatedSymbol())
                .url(normalizeCanonicalUrl(item.getUrl()))
                .imageUrl(hasText(item.getImageUrl()) ? item.getImageUrl().trim() : item.getImageUrl())
                .publishedAt(resolvedPublishedAt)
                .contentEnrichedAt(item.getContentEnrichedAt())
                .qualityStatus(hasText(item.getQualityStatus()) ? item.getQualityStatus().trim() : item.getQualityStatus())
                .isKapDisclosure(item.getIsKapDisclosure())
                .disclosureType(hasText(item.getDisclosureType()) ? item.getDisclosureType().trim() : item.getDisclosureType())
                .contentSections(item.getContentSections())
                .classificationRejectReason(classificationRejectReason)
                .build();
    }

    private String deriveClassificationPreview(NewsItemDto item) {
        if (item == null) {
            return null;
        }
        if (hasText(item.getSummary())) {
            return item.getSummary();
        }
        if (!hasText(item.getContentSections())) {
            return null;
        }
        String preview = item.getContentSections()
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('{', ' ')
                .replace('}', ' ')
                .replace('"', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return preview.length() <= 400 ? preview : preview.substring(0, 400);
    }

    private String deriveClassificationPreview(News news) {
        if (news == null) {
            return null;
        }
        if (hasText(news.getSummary())) {
            return news.getSummary();
        }
        if (hasText(news.getContentSections())) {
            return sanitizeStructuredPreview(news.getContentSections());
        }
        if (hasText(news.getContentHtml())) {
            return sanitizeStructuredPreview(news.getContentHtml().replaceAll("<[^>]+>", " "));
        }
        return null;
    }

    private String sanitizeStructuredPreview(String value) {
        if (!hasText(value)) {
            return null;
        }
        String preview = value
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('{', ' ')
                .replace('}', ' ')
                .replace('"', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return preview.length() <= 400 ? preview : preview.substring(0, 400);
    }

    private String resolveRepairCategoryHint(News news) {
        if (news == null) {
            return null;
        }
        if (isKapNews(news)) {
            return "DISCLOSURE";
        }
        return "ECONOMY";
    }

    private boolean isKapNews(News news) {
        if (news == null) {
            return false;
        }
        String category = normalizeNullable(news.getCategory());
        return Boolean.TRUE.equals(news.getIsKapDisclosure())
                || NewsProviderType.KAP.name().equalsIgnoreCase(news.getProvider())
                || "DISCLOSURE".equalsIgnoreCase(category)
                || "SPECIAL_DISCLOSURE".equalsIgnoreCase(category)
                || "FINANCIAL_REPORT".equalsIgnoreCase(category);
    }

    private boolean sameCategory(String left, String right) {
        if (!hasText(left) && !hasText(right)) {
            return true;
        }
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private String buildCategoryRepairReason(String oldCategory, String newCategory) {
        return "Current classifier accepted " + newCategory
                + " using neutral repair hint ECONOMY; old stored category was "
                + (hasText(oldCategory) ? oldCategory : "blank")
                + ". Classifier rules were not changed.";
    }

    private void addCategoryRepairSample(
            List<NewsCategoryRepairResponseDto.SampleChangeDto> sampleChanges,
            News news,
            String oldCategory,
            String newCategory
    ) {
        addCategoryRepairSample(sampleChanges, news, oldCategory, newCategory, null);
    }

    private void addCategoryRepairSample(
            List<NewsCategoryRepairResponseDto.SampleChangeDto> sampleChanges,
            News news,
            String oldCategory,
            String newCategory,
            String repairReason
    ) {
        NewsCategoryRepairResponseDto.SampleChangeDto sample = NewsCategoryRepairResponseDto.SampleChangeDto.builder()
                .id(news.getId())
                .title(truncateForLog(news.getTitle()))
                .oldCategory(oldCategory)
                .newCategory(newCategory)
                .reason(hasText(repairReason) ? repairReason : buildCategoryRepairReason(oldCategory, newCategory))
                .build();

        if (sampleChanges.size() < CATEGORY_REPAIR_SAMPLE_LIMIT) {
            sampleChanges.add(sample);
            return;
        }
        if (!isPriorityRepairCategory(oldCategory)) {
            return;
        }
        for (int i = 0; i < sampleChanges.size(); i++) {
            NewsCategoryRepairResponseDto.SampleChangeDto existing = sampleChanges.get(i);
            if (!isPriorityRepairCategory(existing.getOldCategory())) {
                sampleChanges.set(i, sample);
                return;
            }
        }
    }

    private boolean isPriorityRepairCategory(String category) {
        if (!hasText(category)) {
            return false;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return NewsCategoryClassifier.FX.equals(normalized)
                || NewsCategoryClassifier.BANKING.equals(normalized)
                || NewsCategoryClassifier.GOLD_COMMODITY.equals(normalized)
                || NewsCategoryClassifier.GEOPOLITICS.equals(normalized);
    }

    private boolean isStaleCategoryRepairFallback(String oldCategory) {
        if (!hasText(oldCategory)) {
            return true;
        }
        String normalized = oldCategory.trim().toUpperCase(Locale.ROOT);
        return NewsCategoryClassifier.FX.equals(normalized)
                || NewsCategoryClassifier.GEOPOLITICS.equals(normalized)
                || NewsCategoryClassifier.ENERGY.equals(normalized)
                || NewsCategoryClassifier.INTEREST_BONDS.equals(normalized);
    }

    private boolean shouldUpdateCategory(News existingNews, NewsItemDto item) {
        if (existingNews == null || item == null || Boolean.TRUE.equals(existingNews.getIsKapDisclosure())) {
            return false;
        }
        if (!hasText(item.getCategory()) || !newsCategoryClassifier.isPrimaryCategory(item.getCategory())) {
            return false;
        }
        return !newsCategoryClassifier.isPrimaryCategory(existingNews.getCategory());
    }


    private String normalizeNullable(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int calculateFinancialRelevanceScore(NewsItemDto item) {
        if (item == null) {
            return 0;
        }
        if (Boolean.TRUE.equals(item.getIsKapDisclosure())) {
            return 5;
        }
        String combined = normalizeLookupValue(String.join(" ",
                safeText(item.getTitle()),
                safeText(item.getSummary()),
                safeText(item.getCategory()),
                safeText(item.getUrl())));
        if (!hasText(combined)) {
            return 0;
        }
        int score = 0;
        for (String keyword : FINANCIAL_RELEVANCE_KEYWORDS) {
            if (combined.contains(normalizeLookupValue(keyword))) {
                score++;
            }
        }
        return score;
    }

    private String resolveRejectReason(
            boolean missingExternalId,
            boolean missingTitle,
            boolean missingSource,
            boolean missingUrl,
            boolean missingProvider,
            boolean missingRegionScope,
            boolean missingDate,
            boolean invalidBecauseLength,
            boolean skippedFullContentNotAvailable,
            boolean lowRelevance
    ) {
        if (missingUrl) return "MISSING_URL";
        if (missingTitle) return "MISSING_TITLE";
        if (missingDate) return "MISSING_PUBLISHED_AT";
        if (missingSource) return "MISSING_SOURCE";
        if (missingProvider) return "MISSING_PROVIDER";
        if (missingRegionScope) return "MISSING_REGION_SCOPE";
        if (missingExternalId) return "MISSING_EXTERNAL_ID";
        if (invalidBecauseLength) return "INVALID_LENGTH";
        if (skippedFullContentNotAvailable) return "INSUFFICIENT_CONTENT";
        if (lowRelevance) return "LOW_RELEVANCE";
        return "UNKNOWN";
    }

    static String normalizeCanonicalUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            if (host != null && host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            path = path.replaceAll("/{2,}", "/");
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = normalizeQuery(uri.getRawQuery());
            int port = uri.getPort();
            boolean keepPort = port != -1
                    && !("http".equals(scheme) && port == 80)
                    && !("https".equals(scheme) && port == 443);
            URI normalized = new URI(scheme, null, host, keepPort ? port : -1, path, query != null && !query.isBlank() ? query : null, null);
            return normalized.toASCIIString();
        } catch (URISyntaxException exception) {
            return rawUrl.trim();
        }
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        Map<String, String> kept = new TreeMap<>();
        for (String part : rawQuery.split("&")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String[] pair = part.split("=", 2);
            String key = pair[0].trim();
            if (key.isBlank()) {
                continue;
            }
            String lowerKey = key.toLowerCase(Locale.ROOT);
            if (lowerKey.startsWith("utm_") || TRACKING_QUERY_PARAMS.contains(lowerKey)) {
                continue;
            }
            kept.putIfAbsent(key, pair.length > 1 ? pair[1].trim() : "");
        }
        if (kept.isEmpty()) {
            return null;
        }
        return kept.entrySet().stream()
                .map(entry -> entry.getValue().isEmpty() ? entry.getKey() : entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String buildTitleSourceKey(String title, String source) {
        String normalizedTitle = normalizeLookupValue(title);
        String normalizedSource = normalizeLookupValue(source);
        if (!hasText(normalizedTitle) || !hasText(normalizedSource)) {
            return null;
        }
        return normalizedSource + "|" + normalizedTitle;
    }

    private String normalizeLookupValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        return normalizeText(value)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private int classifyTimeoutCount(Exception exception) {
        return exception instanceof java.net.SocketTimeoutException ? 1 : 0;
    }

    private int classifyParseErrorCount(Exception exception) {
        return exception instanceof IllegalArgumentException ? 1 : 0;
    }

    private NewsSyncProviderResultDto buildProviderResult(
            String provider,
            boolean success,
            int fetchedCount,
            int savedCount,
            int skippedCount,
            int duplicateCount,
            Integer timeoutCount,
            Integer parseErrorCount,
            String errorMessage
    ) {
        return NewsSyncProviderResultDto.builder()
                .provider(provider)
                .success(success)
                .fetchedCount(fetchedCount)
                .savedCount(savedCount)
                .skippedCount(skippedCount)
                .duplicateCount(duplicateCount)
                .timeoutCount(timeoutCount != null ? timeoutCount : 0)
                .parseErrorCount(parseErrorCount != null ? parseErrorCount : 0)
                .errorMessage(errorMessage)
                .build();
    }

    private ValidationResult validateForPersistence(NewsItemDto item) {
        if (item == null) {
            return ValidationResult.invalid(null, true, true, true, true, true, false, false, false, "ITEM_NULL");
        }
        boolean missingExternalId = !hasText(item.getExternalId());
        boolean missingTitle = !hasText(item.getTitle());
        boolean missingSource = !hasText(item.getSource());
        boolean missingUrl = !hasText(item.getUrl());
        boolean missingProvider = !hasText(item.getProvider());
        boolean missingRegionScope = !hasText(item.getRegionScope());
        boolean missingDate = item.getPublishedAt() == null;
        boolean categoryRejected = hasText(item.getClassificationRejectReason());
        boolean skippedFullContentNotAvailable = !isPersistableQuality(item);
        FinancialImpactResult impactResult = financialImpactClassifier.classify(
                item.getTitle(), item.getSummary(), item.getContentSections(),
                item.getProvider(), null, item.getCategory(), item.getUrl());
        boolean lowRelevance = !categoryRejected && !impactResult.marketRelevant();
        String decision = lowRelevance ? "REJECT" : "ACCEPT";
        logger.info("news.ingest.classify: provider={}, title=\"{}\", score={}, marketRelevant={}, " +
                        "confidence={}, impactType={}, decision={}, " +
                        "matchedSignals={}, reason=\"{}\"",
                item.getProvider(),
                item.getTitle() != null ? item.getTitle().substring(0, Math.min(item.getTitle().length(), 80)) : "",
                impactResult.score(), impactResult.marketRelevant(), impactResult.confidence(),
                impactResult.impactType(), decision, impactResult.matchedSignals(), impactResult.reason());
        boolean invalidBecauseLength = exceedsLength(item.getTitle(), TITLE_MAX_LENGTH)
                || exceedsLength(item.getSource(), SOURCE_MAX_LENGTH)
                || exceedsLength(item.getProvider(), PROVIDER_MAX_LENGTH)
                || exceedsLength(item.getLanguage(), LANGUAGE_MAX_LENGTH)
                || exceedsLength(item.getRegionScope(), REGION_SCOPE_MAX_LENGTH)
                || exceedsLength(item.getCategory(), CATEGORY_MAX_LENGTH)
                || exceedsLength(item.getRelatedSymbol(), RELATED_SYMBOL_MAX_LENGTH)
                || exceedsLength(item.getQualityStatus(), QUALITY_STATUS_MAX_LENGTH)
                || exceedsLength(item.getDisclosureType(), DISCLOSURE_TYPE_MAX_LENGTH);
        String rejectReason = categoryRejected
                ? item.getClassificationRejectReason()
                : resolveRejectReason(
                        missingExternalId,
                        missingTitle,
                        missingSource,
                        missingUrl,
                        missingProvider,
                        missingRegionScope,
                        missingDate,
                        invalidBecauseLength,
                        skippedFullContentNotAvailable,
                        lowRelevance
                );

        return new ValidationResult(
                item,
                !(missingExternalId || missingTitle || missingSource || missingUrl || missingProvider || missingRegionScope || missingDate || invalidBecauseLength || categoryRejected || skippedFullContentNotAvailable || lowRelevance),
                missingExternalId,
                missingTitle,
                missingUrl,
                missingSource,
                missingDate,
                invalidBecauseLength,
                skippedFullContentNotAvailable,
                lowRelevance,
                rejectReason
        );
    }

    private boolean isPersistableQuality(NewsItemDto item) {
        if (item == null) {
            return false;
        }
        if (Boolean.TRUE.equals(item.getIsKapDisclosure())) {
            return hasText(item.getSummary()) || hasText(item.getContentSections());
        }
        if ("SOURCE_LINK_ONLY".equalsIgnoreCase(item.getQualityStatus())) {
            return false;
        }
        return hasText(item.getSummary()) && item.getSummary().trim().length() >= MIN_SUMMARY_LENGTH;
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

    private Long resolveFavoriteFilterUserId(NewsSearchRequest request) {
        if (!Boolean.TRUE.equals(request.getFavoritesOnly())) {
            return null;
        }
        Long userId = request.getFavoriteUserId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String keycloakId = extractKeycloakSubject(authentication);

        if (userId == null) {
            return keycloakId == null
                    ? null
                    : userRepository.findByKeycloakId(keycloakId)
                    .map(User::getId)
                    .orElse(null);
        }

        if (hasAuthority(authentication, "ROLE_ADMIN")) {
            return userId;
        }
        boolean allowed = keycloakId != null
                && userRepository.findById(userId)
                .map(user -> keycloakId.equals(user.getKeycloakId()))
                .orElse(false);
        if (!allowed) {
            throw new BadRequestException("favorites filter is only available for the authenticated user");
        }
        return userId;
    }

    private String extractKeycloakSubject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Jwt jwt ? jwt.getSubject() : null;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
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

    private List<String> parseCsvValues(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Pattern.compile(",")
                .splitAsStream(value)
                .map(String::trim)
                .filter(this::hasText)
                .toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private QueryContext resolveQueryContext(NewsSearchRequest request) {
        Set<NewsProviderType> providers = parseCsvValues(request.getProvider()).stream()
                .map(NewsProviderType::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        NewsScope scope = NewsScope.from(request.getScope());

        boolean providerScopeMismatch = providers.stream().anyMatch(provider -> !scope.providers().contains(provider));
        if (providerScopeMismatch) {
            throw new BadRequestException("Selected provider does not match selected scope");
        }

        return new QueryContext(scope, providers);
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
            Sort.Direction resolvedSortDirection,
            Long favoriteUserId
    ) {
        return Specification.allOf(
                byProvider(context),
                byScope(context),
                byCategory(request),
                byLanguage(request),
                byKapDisclosure(request),
                byFavorites(request, favoriteUserId),
                bySymbol(request),
                byKeyword(request),
                byDateRange(request),
                bySort(resolvedSortBy, resolvedSortDirection)
        );
    }

    private Specification<News> byProvider(QueryContext context) {
        if (context.providers.isEmpty()) {
            return null;
        }
        Set<String> providers = context.providers.stream()
                .map(NewsProviderType::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return (root, query, cb) -> root.get("provider").in(providers);
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
        Set<String> categories = parseCsvValues(request.getCategory()).stream()
                .flatMap(category -> newsCategoryClassifier.resolveFilterCategories(category).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categories.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> cb.upper(root.get("category")).in(categories);
    }

    private Specification<News> byLanguage(NewsSearchRequest request) {
        if (!hasText(request.getLanguage())) {
            return null;
        }
        Set<String> languages = parseCsvValues(request.getLanguage()).stream()
                .map(language -> language.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (languages.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> cb.lower(root.get("language")).in(languages);
    }

    private Specification<News> byKapDisclosure(NewsSearchRequest request) {
        if (request.getIsKapDisclosure() == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("isKapDisclosure"), request.getIsKapDisclosure());
    }

    private Specification<News> byFavorites(NewsSearchRequest request, Long favoriteUserId) {
        if (!Boolean.TRUE.equals(request.getFavoritesOnly())) {
            return null;
        }
        if (favoriteUserId == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            var subquery = query.subquery(Long.class);
            var favoriteRoot = subquery.from(NewsFavorite.class);
            subquery.select(favoriteRoot.get("news").get("id"))
                    .where(cb.equal(favoriteRoot.get("user").get("id"), favoriteUserId));
            return root.get("id").in(subquery);
        };
    }

    private Specification<News> bySymbol(NewsSearchRequest request) {
        if (!hasText(request.getSymbol())) {
            return null;
        }
        String symbol = normalizeSymbol(request.getSymbol());
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("isKapDisclosure")),
                cb.equal(cb.upper(root.get("relatedSymbol")), symbol)
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

    private List<RelatedNewsItemDto> resolveRelatedNews(News news) {
        LinkedHashMap<Long, News> related = new LinkedHashMap<>();
        Pageable firstPage = PageRequest.of(0, MAX_RELATED_NEWS, Sort.by(Sort.Direction.DESC, "publishedAt")
                .and(Sort.by(Sort.Direction.DESC, "createdAt")));

        LocalDateTime basePublishedAt = news.getPublishedAt() != null ? news.getPublishedAt() : LocalDateTime.now();
        if (hasText(news.getCategory())) {
            addRelatedNews(related, newsRepository.findRelatedByCategorySince(
                    news.getId(),
                    news.getCategory().trim(),
                    basePublishedAt.minusDays(7),
                    firstPage
            ));
        }

        if (related.size() < MAX_RELATED_NEWS && hasText(news.getSource())) {
            addRelatedNews(related, newsRepository.findRelatedBySourceSince(
                    news.getId(),
                    news.getSource().trim(),
                    basePublishedAt.minusDays(14),
                    firstPage
            ));
        }

        if (related.size() < MAX_RELATED_NEWS && hasText(news.getCategory())) {
            addRelatedNews(related, newsRepository.findRelatedByCategory(
                    news.getId(),
                    news.getCategory().trim(),
                    firstPage
            ));
        }

        return related.values().stream()
                .limit(MAX_RELATED_NEWS)
                .map(this::toRelatedNewsItem)
                .toList();
    }

    private void addRelatedNews(LinkedHashMap<Long, News> target, List<News> candidates) {
        if (candidates == null || candidates.isEmpty() || target.size() >= MAX_RELATED_NEWS) {
            return;
        }
        for (News candidate : candidates) {
            if (candidate == null || candidate.getId() == null || target.containsKey(candidate.getId())) {
                continue;
            }
            target.put(candidate.getId(), candidate);
            if (target.size() >= MAX_RELATED_NEWS) {
                return;
            }
        }
    }

    private RelatedNewsItemDto toRelatedNewsItem(News news) {
        return RelatedNewsItemDto.builder()
                .id(news.getId())
                .title(news.getTitle())
                .sourceName(newsPresentationMapper.toResponse(news).getSourceName())
                .category(news.getCategory())
                .publishedAt(news.getPublishedAt())
                .importanceScore(news.getImportanceScore())
                .build();
    }
    private String normalizeText(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('\u0131', 'i')
                .replace('\u0130', 'I')
                .replaceAll("\\p{M}+", "");
        return normalized.toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private record QueryContext(NewsScope scope, Set<NewsProviderType> providers) {
    }

    private record ValidationResult(
            NewsItemDto item,
            boolean valid,
            boolean missingExternalId,
            boolean missingTitle,
            boolean missingUrl,
            boolean missingSource,
            boolean missingDate,
            boolean invalidBecauseLength,
            boolean skippedFullContentNotAvailable,
            boolean lowRelevance,
            String rejectReason
    ) {
        private static ValidationResult invalid(
                NewsItemDto item,
                boolean missingExternalId,
                boolean missingTitle,
                boolean missingUrl,
                boolean missingSource,
                boolean missingDate,
                boolean invalidBecauseLength,
                boolean skippedFullContentNotAvailable,
                boolean lowRelevance,
                String rejectReason
        ) {
            return new ValidationResult(
                    item,
                    false,
                    missingExternalId,
                    missingTitle,
                    missingUrl,
                    missingSource,
                    missingDate,
                    invalidBecauseLength,
                    skippedFullContentNotAvailable,
                    lowRelevance,
                    rejectReason
            );
        }
    }

    private record ExistingNewsLookup(
            Map<String, News> byExternalId,
            Map<String, News> byNormalizedUrl,
            Map<String, News> byTitleSource
    ) {
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
            int skippedFullContentNotAvailableCount,
            String rejectReasonSummary
    ) {
        private static PersistenceStats empty() {
            return new PersistenceStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "{}");
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







