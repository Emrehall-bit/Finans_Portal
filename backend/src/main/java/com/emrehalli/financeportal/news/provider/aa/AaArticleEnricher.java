package com.emrehalli.financeportal.news.provider.aa;

import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches AA article pages and extracts both the og:image and full body text
 * in a single HTTP call. Uses an in-memory cache and a per-cycle request cap
 * to prevent NÃ—HTTP overhead during feed sync.
 */
@Component
public class AaArticleEnricher {

    private static final Logger logger = LogManager.getLogger(AaArticleEnricher.class);
    private static final int MAX_FETCHES_PER_CYCLE = 15;
    private static final int MIN_CONTENT_LENGTH = 500;
    private static final int MIN_PARAGRAPH_LENGTH = 20;

    private final RestTemplate restTemplate;
    private final RssFeedSupport rssFeedSupport;

    private final ConcurrentHashMap<String, Optional<AaArticleEnrichment>> cache = new ConcurrentHashMap<>();

    public AaArticleEnricher(RestTemplate restTemplate, RssFeedSupport rssFeedSupport) {
        this.restTemplate = restTemplate;
        this.rssFeedSupport = rssFeedSupport;
    }

    /**
     * Fetches and parses the article page. Returns {@link AaArticleEnrichment#EMPTY}
     * on cache miss beyond the rate limit, fetch failure, or parse failure.
     * Never throws.
     */
    public AaArticleEnrichment enrich(String articleUrl, AtomicInteger cycleCounter) {
        if (articleUrl == null || articleUrl.isBlank()) {
            return AaArticleEnrichment.EMPTY;
        }

        Optional<AaArticleEnrichment> cached = cache.get(articleUrl);
        if (cached != null) {
            logger.debug("AA article enrichment cache hit. url: {}", articleUrl);
            return cached.orElse(AaArticleEnrichment.EMPTY);
        }

        if (cycleCounter.get() >= MAX_FETCHES_PER_CYCLE) {
            logger.debug("AA article fetch skipped â€” per-cycle limit ({}) reached. url: {}", MAX_FETCHES_PER_CYCLE, articleUrl);
            return AaArticleEnrichment.EMPTY;
        }

        cycleCounter.incrementAndGet();
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(articleUrl, HttpMethod.GET, null, byte[].class);
            String html = rssFeedSupport.decodeResponseBody(response, logger, "AA article");
            String imageUrl = rssFeedSupport.extractPreferredImageUrlFromDocument(html, articleUrl);
            String content = extractContent(html, articleUrl);

            AaArticleEnrichment enrichment = new AaArticleEnrichment(imageUrl, content);
            cache.put(articleUrl, Optional.of(enrichment));
            logger.debug("AA article enriched. url: {}, hasImage: {}, contentLength: {}",
                    articleUrl, imageUrl != null, content != null ? content.length() : 0);
            return enrichment;
        } catch (Exception e) {
            logger.warn("AA article fetch failed. url: {}, reason: {}", articleUrl, e.getMessage());
            cache.put(articleUrl, Optional.empty());
            return AaArticleEnrichment.EMPTY;
        }
    }

    public AtomicInteger newCycleCounter() {
        return new AtomicInteger(0);
    }

    private String extractContent(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            logger.debug("AA article content: empty html. url: {}", baseUrl);
            return null;
        }
        try {
            Document doc = Jsoup.parse(html, baseUrl);

            // Primary: known AA content container
            Element root = doc.selectFirst("div.embed-responsive.prose");
            if (root == null) {
                logger.debug("AA article content: container not found (div.embed-responsive.prose). url: {}", baseUrl);
                return null;
            }
            logger.debug("AA article content: container found. url: {}", baseUrl);

            // Remove in-container noise before text extraction
            root.select("script, style, nav, footer, aside, noscript, .social, .share, .tags, .related, .breadcrumb").remove();

            // Extract p and h2 elements in document order, preserving structure
            List<String> parts = new ArrayList<>();
            for (Element el : root.select("p, h2")) {
                String text = rssFeedSupport.clean(el.text());
                if (text == null || text.length() < MIN_PARAGRAPH_LENGTH) {
                    continue;
                }
                if (isSpamText(text)) {
                    continue;
                }
                // Mark subheadings so the frontend can render them distinctly
                parts.add(el.tagName().equals("h2") ? "## " + text : text);
            }

            logger.debug("AA article content: {} elements extracted. url: {}", parts.size(), baseUrl);

            if (parts.isEmpty()) {
                logger.debug("AA article content: no valid elements after filtering. url: {}", baseUrl);
                return null;
            }

            String content = String.join("\n\n", parts);
            if (content.length() < MIN_CONTENT_LENGTH) {
                logger.debug("AA article content: too short ({} chars), using fallback. url: {}", content.length(), baseUrl);
                return null;
            }

            logger.debug("AA article content: extracted {} chars. url: {}", content.length(), baseUrl);
            return content;
        } catch (Exception e) {
            logger.debug("AA article content: parse failed. url: {}, reason: {}", baseUrl, e.getMessage());
            return null;
        }
    }

    private boolean isSpamText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("paylaÅŸ")
                || lower.contains("ilgili konular")
                || lower.contains("reklam")
                || lower.contains("abone ol")
                || lower.contains("haberleri takip")
                || lower.contains("tÃ¼mÃ¼nÃ¼ gÃ¶r")
                || lower.startsWith("aa/")
                || lower.startsWith("(aa)");
    }
}




