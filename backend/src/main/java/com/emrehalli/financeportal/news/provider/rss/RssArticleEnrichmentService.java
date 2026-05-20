package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RssArticleEnrichmentService {

    private static final Logger logger = LogManager.getLogger(RssArticleEnrichmentService.class);
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_FETCHES_PER_CYCLE = 20;
    private static final int MAX_ATTEMPTS = 2;
    private static final int FETCH_DELAY_MS = 120;

    private final List<ArticleExtractor> extractors;
    private final GenericArticleExtractor genericArticleExtractor;
    private final RssFeedSupport rssFeedSupport;
    private final NewsRepository newsRepository;
    private final ConcurrentHashMap<String, Optional<RssArticleEnrichment>> cache = new ConcurrentHashMap<>();

    public RssArticleEnrichmentService(
            List<ArticleExtractor> extractors,
            GenericArticleExtractor genericArticleExtractor,
            RssFeedSupport rssFeedSupport,
            NewsRepository newsRepository
    ) {
        this.extractors = extractors;
        this.genericArticleExtractor = genericArticleExtractor;
        this.rssFeedSupport = rssFeedSupport;
        this.newsRepository = newsRepository;
    }

    public AtomicInteger newCycleCounter() {
        return new AtomicInteger(0);
    }

    public RssArticleEnrichment enrich(NewsProviderType providerType, String articleUrl, AtomicInteger cycleCounter) {
        if (articleUrl == null || articleUrl.isBlank()) {
            return RssArticleEnrichment.EMPTY;
        }

        Optional<RssArticleEnrichment> cached = cache.get(articleUrl);
        if (cached != null) {
            return cached.orElse(RssArticleEnrichment.EMPTY);
        }

        Optional<News> stored = newsRepository.findFirstByUrlAndContentEnrichedAtIsNotNullOrderByContentEnrichedAtDesc(articleUrl);
        if (stored.isPresent()) {
            RssArticleEnrichment enrichment = new RssArticleEnrichment(
                    stored.get().getTitle(),
                    stored.get().getImageUrl(),
                    stored.get().getPublishedAt(),
                    stored.get().getSummary(),
                    stored.get().getQualityStatus(),
                    stored.get().getContentEnrichedAt()
            );
            cache.put(articleUrl, Optional.of(enrichment));
            return enrichment;
        }

        if (cycleCounter.get() >= MAX_FETCHES_PER_CYCLE) {
            logger.debug("Article enrichment skipped because per-cycle limit reached. provider: {}, url: {}",
                    providerType.name(), articleUrl);
            return RssArticleEnrichment.EMPTY;
        }

        cycleCounter.incrementAndGet();
        sleepQuietly(FETCH_DELAY_MS);
        RssArticleEnrichment enrichment = fetchAndExtract(providerType, articleUrl);
        cache.put(articleUrl, enrichment.hasEnrichment() ? Optional.of(enrichment) : Optional.empty());
        return enrichment;
    }

    private RssArticleEnrichment fetchAndExtract(NewsProviderType providerType, String articleUrl) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Document document = loadDocument(articleUrl);
                ArticleExtractionResult result = extract(providerType, document, articleUrl);
                if (!result.hasMeaningfulData()) {
                    logger.warn("Article parse returned no useful data. provider: {}, url: {}, attempt: {}",
                            providerType.name(), articleUrl, attempt);
                    continue;
                }

                String cleanedContent = rssFeedSupport.clean(result.fullContent());
                String qualityStatus = resolveQualityStatus(cleanedContent);
                return new RssArticleEnrichment(
                        rssFeedSupport.clean(result.title()),
                        sanitizeImageUrl(result.imageUrl()),
                        result.publishedAt(),
                        cleanedContent,
                        qualityStatus,
                        LocalDateTime.now()
                );
            } catch (SocketTimeoutException exception) {
                logger.warn("Article enrichment timed out. provider: {}, url: {}, attempt: {}, reason: {}",
                        providerType.name(), articleUrl, attempt, exception.getMessage());
            } catch (ArticleFetchException exception) {
                logger.warn("Article enrichment failed. provider: {}, url: {}, attempt: {}, status: {}, reason: {}",
                        providerType.name(), articleUrl, attempt, exception.statusCode(), exception.getMessage());
                if (exception.statusCode() == 403 || exception.statusCode() == 404) {
                    break;
                }
            } catch (Exception exception) {
                logger.warn("Article enrichment parse failed. provider: {}, url: {}, attempt: {}, reason: {}",
                        providerType.name(), articleUrl, attempt, exception.getMessage());
            }
        }

        return RssArticleEnrichment.EMPTY;
    }

    private Document loadDocument(String articleUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(articleUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinancePortalNewsBot/1.0");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.connect();

        int statusCode = connection.getResponseCode();
        if (statusCode >= 400) {
            throw new ArticleFetchException(statusCode, "HTTP " + statusCode);
        }

        try (InputStream inputStream = connection.getInputStream()) {
            return Jsoup.parse(inputStream, null, articleUrl);
        } finally {
            connection.disconnect();
        }
    }

    private ArticleExtractionResult extract(NewsProviderType providerType, Document document, String articleUrl) {
        for (ArticleExtractor extractor : extractors) {
            if (!extractor.supports(providerType) || extractor == genericArticleExtractor) {
                continue;
            }
            ArticleExtractionResult result = extractor.extract(document, articleUrl);
            if (result.hasMeaningfulData()) {
                String content = rssFeedSupport.clean(result.fullContent());
                if (content != null && content.length() >= 150) {
                    return result;
                }
            }
        }
        return genericArticleExtractor.extract(document, articleUrl);
    }

    private String resolveQualityStatus(String content) {
        int length = content == null ? 0 : content.length();
        if (length >= 500) {
            return NewsQualityStatus.FULL_CONTENT.name();
        }
        if (length >= 150) {
            return NewsQualityStatus.SUMMARY_ONLY.name();
        }
        return NewsQualityStatus.SOURCE_LINK_ONLY.name();
    }

    private String sanitizeImageUrl(String imageUrl) {
        String cleaned = rssFeedSupport.clean(imageUrl);
        if (cleaned == null) {
            return null;
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.contains("/logo/") || normalized.contains("_logo.") || normalized.endsWith("logo.jpeg")
                || normalized.endsWith("logo.jpg") || normalized.endsWith("logo.png") || normalized.endsWith("logo.webp")) {
            return null;
        }
        return cleaned;
    }

    private void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ArticleFetchException extends Exception {
        private final int statusCode;

        private ArticleFetchException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
