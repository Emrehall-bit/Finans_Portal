package com.emrehalli.financeportal.news.provider.aa.client;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.provider.aa.AaArticleEnricher;
import com.emrehalli.financeportal.news.provider.aa.AaArticleEnrichment;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AaRssNewsClient {

    private static final Logger logger = LogManager.getLogger(AaRssNewsClient.class);

    private final RestTemplate restTemplate;
    private final AaNewsProperties properties;
    private final RssFeedSupport rssFeedSupport;
    private final AaArticleEnricher aaArticleEnricher;
    private final AtomicReference<ProviderSyncDiagnostics> lastDiagnostics =
            new AtomicReference<>(ProviderSyncDiagnostics.empty(NewsProviderType.AA_RSS.name()));

    public AaRssNewsClient(
            RestTemplate restTemplate,
            AaNewsProperties properties,
            RssFeedSupport rssFeedSupport,
            AaArticleEnricher aaArticleEnricher
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.rssFeedSupport = rssFeedSupport;
        this.aaArticleEnricher = aaArticleEnricher;
    }

    /**
     * Fetches a single RSS feed by URL, parsing items with the given category override.
     * Updates {@code lastDiagnostics} with the result of this fetch.
     */
    public List<NewsItemDto> fetchFeed(String feedName, String feedUrl, String category) {
        AtomicInteger cycleCounter = aaArticleEnricher.newCycleCounter();
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    feedUrl, HttpMethod.GET, null, byte[].class);
            String payload = decodeResponseBody(response);
            if (payload == null || payload.isBlank()) {
                logger.warn("AA RSS response was empty. provider=AA_RSS, feed={}, url={}", feedName, feedUrl);
                lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                        .provider(NewsProviderType.AA_RSS.name())
                        .enabled(true)
                        .feedUrlCount(1)
                        .fetched(0)
                        .fetchedFromFeed(0)
                        .timeoutCount(0)
                        .parseErrorCount(0)
                        .errorMessage("AA RSS response was empty")
                        .lastErrors(List.of("EMPTY_RESPONSE"))
                        .build());
                return List.of();
            }

            List<NewsItemDto> items = parseFeed(payload, feedUrl, category, cycleCounter);
            logger.info("AA feed parsed. provider=AA_RSS, feed={}, url={}, fetched={}, articleFetches={}",
                    feedName, feedUrl, items.size(), cycleCounter.get());
            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.AA_RSS.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(items.size())
                    .fetchedFromFeed(items.size())
                    .timeoutCount(0)
                    .parseErrorCount(0)
                    .errorMessage(null)
                    .lastErrors(List.of())
                    .build());
            return items;
        } catch (RestClientException e) {
            logger.error("AA RSS fetch failed. provider=AA_RSS, feed={}, url={}", feedName, feedUrl, e);
            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.AA_RSS.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(0).fetchedFromFeed(0).timeoutCount(0).parseErrorCount(0)
                    .errorMessage("AA RSS fetch failed: " + e.getClass().getSimpleName())
                    .lastErrors(List.of(e.getClass().getSimpleName()))
                    .build());
            return List.of();
        } catch (Exception e) {
            logger.error("Unexpected AA RSS fetch failure. provider=AA_RSS, feed={}, url={}", feedName, feedUrl, e);
            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.AA_RSS.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(0).fetchedFromFeed(0).timeoutCount(0).parseErrorCount(0)
                    .errorMessage("Unexpected AA RSS fetch failure: " + e.getClass().getSimpleName())
                    .lastErrors(List.of(e.getClass().getSimpleName()))
                    .build());
            return List.of();
        }
    }

    /** @deprecated Use {@link #fetchFeed(String, String, String)} via multi-feed config. */
    public List<NewsItemDto> fetchEconomyNews() {
        String legacyUrl = properties.getRssUrl() != null && !properties.getRssUrl().isBlank()
                ? properties.getRssUrl()
                : "https://www.aa.com.tr/tr/rss/default?cat=ekonomi";
        return fetchFeed("economy", legacyUrl, properties.getDefaultCategory());
    }

    public ProviderSyncDiagnostics getLastDiagnostics() {
        return lastDiagnostics.get();
    }

    String decodeResponseBody(ResponseEntity<byte[]> response) {
        return rssFeedSupport.decodeResponseBody(response, logger, "AA RSS");
    }

    /**
     * Parses a feed payload using the given base URL (for relative link resolution) and category.
     * Package-private to allow unit testing.
     */
    List<NewsItemDto> parseFeed(String payload, String baseUrl, String category, AtomicInteger cycleCounter) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        String effectiveCategory = category != null && !category.isBlank()
                ? category
                : properties.getDefaultCategory();
        try {
            List<NewsItemDto> xmlItems = parseXmlFeed(payload, baseUrl, effectiveCategory, cycleCounter);
            if (!xmlItems.isEmpty()) {
                return xmlItems;
            }

            List<NewsItemDto> htmlItems = parseHtmlFeed(payload, baseUrl, effectiveCategory);
            if (!htmlItems.isEmpty()) {
                logger.info("AA feed fallback parser used. url={}, fetched={}", baseUrl, htmlItems.size());
                return htmlItems;
            }

            logger.warn("AA feed did not contain parsable items. url={}", baseUrl);
            return List.of();
        } catch (Exception e) {
            logger.error("Failed to parse AA feed. url={}", baseUrl, e);
            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.AA_RSS.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(0).fetchedFromFeed(0).timeoutCount(0).parseErrorCount(1)
                    .errorMessage("Failed to parse AA feed: " + e.getClass().getSimpleName())
                    .lastErrors(List.of(e.getClass().getSimpleName()))
                    .build());
            return List.of();
        }
    }

    /** Backward-compat wrapper for tests. */
    List<NewsItemDto> parse(String payload, AtomicInteger cycleCounter) {
        String baseUrl = properties.getRssUrl() != null ? properties.getRssUrl() : "https://www.aa.com.tr/";
        return parseFeed(payload, baseUrl, properties.getDefaultCategory(), cycleCounter);
    }

    /** Backward-compat wrapper for tests. */
    List<NewsItemDto> parse(String payload) {
        return parse(payload, new AtomicInteger(0));
    }

    private List<NewsItemDto> parseXmlFeed(String xml, String baseUrl, String category, AtomicInteger cycleCounter) {
        Document document = Jsoup.parse(xml, baseUrl, Parser.xmlParser());
        Elements entries = document.select("channel > item, feed > entry");
        List<NewsItemDto> items = new ArrayList<>();

        for (Element entry : entries) {
            String title = rssFeedSupport.text(entry, "title");
            String link = rssFeedSupport.resolveXmlLink(entry);
            String guid = rssFeedSupport.firstNonBlank(
                    rssFeedSupport.text(entry, "guid"),
                    rssFeedSupport.text(entry, "id")
            );
            String descriptionHtml = rssFeedSupport.html(entry, "description");
            String rssSummary = rssFeedSupport.firstNonBlank(
                    rssFeedSupport.extractTextFromHtml(descriptionHtml),
                    rssFeedSupport.text(entry, "description"),
                    rssFeedSupport.text(entry, "summary"),
                    rssFeedSupport.text(entry, "content|encoded"),
                    rssFeedSupport.text(entry, "content\\:encoded"),
                    rssFeedSupport.text(entry, "content")
            );
            String pubDate = rssFeedSupport.firstNonBlank(
                    rssFeedSupport.text(entry, "pubDate"),
                    rssFeedSupport.text(entry, "dc|date"),
                    rssFeedSupport.text(entry, "dc\\:date"),
                    rssFeedSupport.text(entry, "updated"),
                    rssFeedSupport.text(entry, "published")
            );

            AaArticleEnrichment enrichment = aaArticleEnricher.enrich(link, cycleCounter);

            String rssImageUrl = rssFeedSupport.resolveImageUrl(entry, descriptionHtml);
            String imageUrl = rssImageUrl != null ? rssImageUrl : enrichment.imageUrl();

            String summary = selectBestSummary(rssSummary, enrichment.content());
            boolean fullContent = isFullContentSelected(rssSummary, enrichment.content(), summary);

            if (enrichment.content() != null) {
                logger.debug("AA article content enriched. url={}, rssSummaryLen={}, contentLen={}",
                        link,
                        rssSummary != null ? rssSummary.length() : 0,
                        enrichment.content().length());
            }

            NewsItemDto item = buildItem(title, link, guid, summary, imageUrl, pubDate, fullContent, category);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    private List<NewsItemDto> parseHtmlFeed(String html, String baseUrl, String category) {
        Document document = Jsoup.parse(html, baseUrl);
        Elements anchors = document.select("a[href]");
        Set<String> seenLinks = new LinkedHashSet<>();
        List<NewsItemDto> items = new ArrayList<>();

        for (Element anchor : anchors) {
            String link = rssFeedSupport.clean(anchor.absUrl("href"));
            if (!isEconomyArticleLink(link) || !seenLinks.add(link)) {
                continue;
            }

            String title = rssFeedSupport.clean(anchor.text());
            if (title == null || title.length() < 12) {
                Element article = anchor.closest("article");
                title = rssFeedSupport.firstNonBlank(
                        title,
                        article == null ? null : rssFeedSupport.text(article, "h1, h2, h3, h4")
                );
            }

            NewsItemDto item = buildItem(title, link, null, null, null, null, false, category);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    private NewsItemDto buildItem(String title, String link, String guid, String summary,
                                  String imageUrl, String pubDate, boolean fullContent, String category) {
        if (title == null || link == null) {
            return null;
        }

        String normalizedSummary = normalizeSummary(summary);

        return NewsItemDto.builder()
                .externalId(rssFeedSupport.resolveExternalId(NewsProviderType.AA_RSS.name(), guid, link))
                .title(title)
                .summary(normalizedSummary)
                .source("Anadolu Ajansı")
                .provider(NewsProviderType.AA_RSS.name())
                .language(properties.getDefaultLanguage())
                .regionScope(properties.getDefaultRegionScope())
                .category(category)
                .relatedSymbol(null)
                .url(link)
                .imageUrl(imageUrl)
                .publishedAt(rssFeedSupport.parsePublishedAt(pubDate, logger, "AA RSS"))
                .qualityStatus(resolveQualityStatus(normalizedSummary, fullContent))
                .isKapDisclosure(false)
                .build();
    }

    private boolean isEconomyArticleLink(String link) {
        if (link == null) {
            return false;
        }
        return link.contains("/tr/ekonomi/") || link.contains("/ekonomi/");
    }

    private String selectBestSummary(String rssSummary, String articleContent) {
        String cleanedContent = rssFeedSupport.clean(articleContent);
        if (cleanedContent == null) {
            return rssSummary;
        }
        String cleanedRss = rssFeedSupport.clean(rssSummary);
        if (cleanedRss == null) {
            return cleanedContent;
        }
        if (cleanedContent.length() >= 500 && cleanedContent.length() > cleanedRss.length() + 150) {
            return cleanedContent;
        }
        return cleanedRss;
    }

    private String normalizeSummary(String summary) {
        String cleaned = rssFeedSupport.clean(summary);
        if (cleaned == null) {
            return null;
        }
        if (cleaned.contains("<") && cleaned.contains(">")) {
            return rssFeedSupport.extractTextFromHtml(cleaned);
        }
        return cleaned;
    }

    private boolean isFullContentSelected(String rssSummary, String articleContent, String selectedSummary) {
        String cleanedArticle = rssFeedSupport.clean(articleContent);
        if (cleanedArticle == null || selectedSummary == null) {
            return false;
        }
        String cleanedRss = rssFeedSupport.clean(rssSummary);
        return cleanedArticle.equals(selectedSummary)
                && (cleanedRss == null || !cleanedArticle.equals(cleanedRss));
    }

    private String resolveQualityStatus(String normalizedSummary, boolean fullContent) {
        if (fullContent) {
            return NewsQualityStatus.FULL_CONTENT.name();
        }
        if (normalizedSummary != null) {
            return NewsQualityStatus.SUMMARY_ONLY.name();
        }
        return NewsQualityStatus.SOURCE_LINK_ONLY.name();
    }
}



