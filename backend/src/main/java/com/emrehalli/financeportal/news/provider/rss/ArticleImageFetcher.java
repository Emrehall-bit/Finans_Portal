package com.emrehalli.financeportal.news.provider.rss;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches article images with an in-memory URL cache and a per-cycle request cap
 * to prevent N×HTTP overhead during feed sync.
 */
@Component
public class ArticleImageFetcher {

    private static final Logger logger = LogManager.getLogger(ArticleImageFetcher.class);
    private static final int MAX_FETCHES_PER_CYCLE = 15;

    private final RestTemplate restTemplate;
    private final RssFeedSupport rssFeedSupport;

    // Caches resolved image URLs (null result stored as Optional.empty) to avoid re-fetching
    private final ConcurrentHashMap<String, Optional<String>> imageCache = new ConcurrentHashMap<>();

    public ArticleImageFetcher(RestTemplate restTemplate, RssFeedSupport rssFeedSupport) {
        this.restTemplate = restTemplate;
        this.rssFeedSupport = rssFeedSupport;
    }

    /**
     * Fetches the preferred image URL for an article page.
     * Returns null if not found, rate limit exceeded, or fetch fails.
     *
     * @param articleUrl    the article page URL to inspect
     * @param cycleCounter  shared counter for this sync cycle; incremented per actual HTTP call
     * @param providerName  used for log context
     */
    public String fetchImageUrl(String articleUrl, AtomicInteger cycleCounter, String providerName) {
        if (articleUrl == null || articleUrl.isBlank()) {
            return null;
        }

        Optional<String> cached = imageCache.get(articleUrl);
        if (cached != null) {
            logger.debug("{} article image cache hit. url: {}", providerName, articleUrl);
            return cached.orElse(null);
        }

        if (cycleCounter.get() >= MAX_FETCHES_PER_CYCLE) {
            logger.debug("{} article image fetch skipped — per-cycle limit ({}) reached. url: {}", providerName, MAX_FETCHES_PER_CYCLE, articleUrl);
            return null;
        }

        cycleCounter.incrementAndGet();
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(articleUrl, HttpMethod.GET, null, byte[].class);
            String payload = rssFeedSupport.decodeResponseBody(response, logger, providerName + " article");
            String imageUrl = rssFeedSupport.extractPreferredImageUrlFromDocument(payload, articleUrl);
            imageCache.put(articleUrl, Optional.ofNullable(imageUrl));
            if (imageUrl != null) {
                logger.debug("{} article image fetched. url: {}, imageUrl: {}", providerName, articleUrl, imageUrl);
            } else {
                logger.debug("{} article image not found in document. url: {}", providerName, articleUrl);
            }
            return imageUrl;
        } catch (Exception e) {
            logger.warn("{} article image fetch failed. url: {}, reason: {}", providerName, articleUrl, e.getMessage());
            imageCache.put(articleUrl, Optional.empty());
            return null;
        }
    }

    /** Creates a fresh per-cycle counter to be shared across all items in one sync run. */
    public AtomicInteger newCycleCounter() {
        return new AtomicInteger(0);
    }
}



