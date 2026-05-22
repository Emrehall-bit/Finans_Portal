package com.emrehalli.financeportal.news.provider.cnbc;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.rss.RssArticleEnrichment;
import com.emrehalli.financeportal.news.provider.rss.RssArticleEnrichmentService;
import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
import com.emrehalli.financeportal.news.provider.rss.RssProviderFetchResult;
import com.emrehalli.financeportal.news.provider.rss.RssProviderRelevanceFilter;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CnbcRssNewsClient {

    private static final Logger logger = LogManager.getLogger(CnbcRssNewsClient.class);

    private final RestTemplate restTemplate;
    private final CnbcNewsProperties properties;
    private final RssFeedSupport rssFeedSupport;
    private final RssArticleEnrichmentService enrichmentService;
    private final RssProviderRelevanceFilter relevanceFilter;
    private final AtomicReference<ProviderSyncDiagnostics> lastDiagnostics =
            new AtomicReference<>(ProviderSyncDiagnostics.empty(NewsProviderType.CNBC_RSS.name()));

    public CnbcRssNewsClient(
            RestTemplate restTemplate,
            CnbcNewsProperties properties,
            RssFeedSupport rssFeedSupport,
            RssArticleEnrichmentService enrichmentService,
            RssProviderRelevanceFilter relevanceFilter
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.rssFeedSupport = rssFeedSupport;
        this.enrichmentService = enrichmentService;
        this.relevanceFilter = relevanceFilter;
    }

    public List<NewsItemDto> fetchNews() {
        return fetchNewsWithDiagnostics().getItems();
    }

    public RssProviderFetchResult fetchNewsWithDiagnostics() {
        List<String> feedUrls = properties.getFeedUrls();
        if (!properties.isEnabled()) {
            ProviderSyncDiagnostics diagnostics = ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.CNBC_RSS.name())
                    .enabled(false)
                    .feedUrlCount(feedUrls.size())
                    .fetched(0)
                    .skippedByRelevance(0)
                    .timeoutCount(0)
                    .parseErrorCount(0)
                    .errorMessage("Provider disabled by configuration")
                    .build();
            lastDiagnostics.set(diagnostics);
            return RssProviderFetchResult.builder().items(List.of()).feedUrlCount(feedUrls.size()).skippedByRelevance(0).errorMessage(diagnostics.getErrorMessage()).build();
        }

        List<NewsItemDto> allItems = new ArrayList<>();
        int totalSkippedByRelevance = 0;
        int totalTimeoutCount = 0;
        int totalParseErrorCount = 0;
        String lastErrorMessage = null;
        for (String feedUrl : feedUrls) {
            RssProviderFetchResult result = fetchSingleFeed(feedUrl);
            allItems.addAll(result.getItems());
            totalSkippedByRelevance += result.getSkippedByRelevance();
            if (result.getErrorMessage() != null && result.getErrorMessage().contains("Timeout")) {
                totalTimeoutCount++;
            }
            if (result.getErrorMessage() != null && result.getErrorMessage().toLowerCase().contains("parse")) {
                totalParseErrorCount++;
            }
            if (result.getErrorMessage() != null) {
                lastErrorMessage = result.getErrorMessage();
            }
        }
        lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                .provider(NewsProviderType.CNBC_RSS.name())
                .enabled(true)
                .feedUrlCount(feedUrls.size())
                .fetched(allItems.size())
                .skippedByRelevance(totalSkippedByRelevance)
                .timeoutCount(totalTimeoutCount)
                .parseErrorCount(totalParseErrorCount)
                .errorMessage(lastErrorMessage)
                .build());
        return RssProviderFetchResult.builder()
                .items(allItems)
                .feedUrlCount(feedUrls.size())
                .skippedByRelevance(totalSkippedByRelevance)
                .errorMessage(lastErrorMessage)
                .build();
    }

    public ProviderSyncDiagnostics getLastDiagnostics() {
        return lastDiagnostics.get();
    }

    private RssProviderFetchResult fetchSingleFeed(String feedUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(feedUrl, HttpMethod.GET, null, byte[].class);
            String payload = rssFeedSupport.decodeResponseBody(response, logger, "CNBC RSS");
            if (payload == null || payload.isBlank()) {
                logger.warn("CNBC RSS response was empty. url: {}", feedUrl);
                return RssProviderFetchResult.builder().items(List.of()).feedUrlCount(1).skippedByRelevance(0).errorMessage("CNBC RSS response was empty").build();
            }

            AtomicInteger cycleCounter = enrichmentService.newCycleCounter();
            ParseResult parsed = parse(payload, cycleCounter);
            logger.info("CNBC feed parsed. url: {}, fetched: {}, skippedByRelevance: {}, enrichedFetches: {}",
                    feedUrl, parsed.items().size(), parsed.skippedByRelevance(), cycleCounter.get());
            return RssProviderFetchResult.builder()
                    .items(parsed.items())
                    .feedUrlCount(1)
                    .skippedByRelevance(parsed.skippedByRelevance())
                    .errorMessage(null)
                    .build();
        } catch (RestClientException exception) {
            logger.error("CNBC RSS fetch failed. url: {}", feedUrl, exception);
            String message = exception.getCause() instanceof java.net.SocketTimeoutException
                    ? "CNBC RSS fetch failed: Timeout"
                    : "CNBC RSS fetch failed: " + exception.getClass().getSimpleName();
            return RssProviderFetchResult.builder().items(List.of()).feedUrlCount(1).skippedByRelevance(0).errorMessage(message).build();
        } catch (Exception exception) {
            logger.error("Unexpected CNBC RSS fetch failure. url: {}", feedUrl, exception);
            return RssProviderFetchResult.builder().items(List.of()).feedUrlCount(1).skippedByRelevance(0).errorMessage("Unexpected CNBC RSS fetch failure: " + exception.getClass().getSimpleName()).build();
        }
    }

    ParseResult parse(String payload, AtomicInteger cycleCounter) {
        if (payload == null || payload.isBlank()) {
            return new ParseResult(List.of(), 0);
        }

        try {
            Document document = Jsoup.parse(payload, properties.getRssUrl(), Parser.xmlParser());
            Elements entries = document.select("channel > item, feed > entry");
            List<NewsItemDto> items = new ArrayList<>();
            int skippedByRelevance = 0;

            for (Element entry : entries) {
                NewsItemDto item = buildItem(entry, cycleCounter);
                if (item != null) {
                    items.add(item);
                } else {
                    String title = rssFeedSupport.text(entry, "title");
                    String link = rssFeedSupport.resolveXmlLink(entry);
                    String descriptionHtml = rssFeedSupport.html(entry, "description");
                    String summary = rssFeedSupport.firstNonBlank(
                            rssFeedSupport.extractTextFromHtml(descriptionHtml),
                            rssFeedSupport.text(entry, "description"),
                            rssFeedSupport.text(entry, "summary"),
                            rssFeedSupport.text(entry, "content")
                    );
                    String category = rssFeedSupport.firstNonBlank(rssFeedSupport.text(entry, "category"), properties.getDefaultCategory());
                    if (!relevanceFilter.isRelevant(NewsProviderType.CNBC_RSS, title, summary, category, link)) {
                        skippedByRelevance++;
                    }
                }
            }
            return new ParseResult(items, skippedByRelevance);
        } catch (Exception exception) {
            logger.error("Failed to parse CNBC feed. url: {}", properties.getRssUrl(), exception);
            return new ParseResult(List.of(), 0);
        }
    }

    private NewsItemDto buildItem(Element entry, AtomicInteger cycleCounter) {
        String title = rssFeedSupport.text(entry, "title");
        String link = rssFeedSupport.resolveXmlLink(entry);
        String guid = rssFeedSupport.firstNonBlank(rssFeedSupport.text(entry, "guid"), rssFeedSupport.text(entry, "id"));
        String descriptionHtml = rssFeedSupport.html(entry, "description");
        String summary = rssFeedSupport.firstNonBlank(
                rssFeedSupport.extractTextFromHtml(descriptionHtml),
                rssFeedSupport.text(entry, "description"),
                rssFeedSupport.text(entry, "summary"),
                rssFeedSupport.text(entry, "content")
        );
        String category = rssFeedSupport.firstNonBlank(rssFeedSupport.text(entry, "category"), properties.getDefaultCategory());

        if (!relevanceFilter.isRelevant(NewsProviderType.CNBC_RSS, title, summary, category, link)) {
            return null;
        }

        RssArticleEnrichment enrichment = enrichmentService.enrich(NewsProviderType.CNBC_RSS, link, cycleCounter);
        String finalTitle = rssFeedSupport.firstNonBlank(enrichment.title(), title);
        String finalSummary = resolveSummary(summary, enrichment);
        String qualityStatus = resolveQualityStatus(finalSummary, enrichment);

        if (!hasText(finalTitle) || !hasText(link)) {
            return null;
        }

        return NewsItemDto.builder()
                .externalId(rssFeedSupport.resolveExternalId(NewsProviderType.CNBC_RSS.name(), guid, link))
                .title(finalTitle)
                .summary(finalSummary)
                .source("CNBC")
                .provider(NewsProviderType.CNBC_RSS.name())
                .language(properties.getDefaultLanguage())
                .regionScope(properties.getDefaultRegionScope())
                .category(category)
                .url(link)
                .imageUrl(rssFeedSupport.firstNonBlank(enrichment.imageUrl(), rssFeedSupport.resolveImageUrl(entry, descriptionHtml)))
                .publishedAt(enrichment.publishedAt() != null ? enrichment.publishedAt() : rssFeedSupport.parsePublishedAt(rssFeedSupport.text(entry, "pubDate"), logger, "CNBC RSS"))
                .qualityStatus(qualityStatus)
                .contentEnrichedAt(enrichment.contentEnrichedAt())
                .isKapDisclosure(false)
                .build();
    }

    private String resolveSummary(String rssSummary, RssArticleEnrichment enrichment) {
        String articleContent = rssFeedSupport.clean(enrichment.content());
        if (articleContent != null) {
            return articleContent;
        }
        return rssFeedSupport.clean(rssSummary);
    }

    private String resolveQualityStatus(String summary, RssArticleEnrichment enrichment) {
        if (hasText(enrichment.qualityStatus())) {
            return enrichment.qualityStatus();
        }
        return hasText(summary) ? NewsQualityStatus.SUMMARY_ONLY.name() : NewsQualityStatus.SOURCE_LINK_ONLY.name();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ParseResult(List<NewsItemDto> items, int skippedByRelevance) {
    }
}
