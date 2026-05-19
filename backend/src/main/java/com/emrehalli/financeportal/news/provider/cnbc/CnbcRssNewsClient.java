package com.emrehalli.financeportal.news.provider.cnbc;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.provider.rss.RssArticleEnrichment;
import com.emrehalli.financeportal.news.provider.rss.RssArticleEnrichmentService;
import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
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
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CnbcRssNewsClient {

    private static final Logger logger = LogManager.getLogger(CnbcRssNewsClient.class);

    private final RestTemplate restTemplate;
    private final CnbcNewsProperties properties;
    private final RssFeedSupport rssFeedSupport;
    private final RssArticleEnrichmentService enrichmentService;
    private final RssProviderRelevanceFilter relevanceFilter;

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
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(properties.getRssUrl(), HttpMethod.GET, null, byte[].class);
            String payload = rssFeedSupport.decodeResponseBody(response, logger, "CNBC RSS");
            if (payload == null || payload.isBlank()) {
                logger.warn("CNBC RSS response was empty");
                return List.of();
            }

            AtomicInteger cycleCounter = enrichmentService.newCycleCounter();
            List<NewsItemDto> items = parse(payload, cycleCounter);
            logger.info("CNBC feed parsed. url: {}, fetched: {}, enrichedFetches: {}",
                    properties.getRssUrl(), items.size(), cycleCounter.get());
            return items;
        } catch (RestClientException exception) {
            logger.error("CNBC RSS fetch failed. url: {}", properties.getRssUrl(), exception);
            return List.of();
        } catch (Exception exception) {
            logger.error("Unexpected CNBC RSS fetch failure. url: {}", properties.getRssUrl(), exception);
            return List.of();
        }
    }

    List<NewsItemDto> parse(String payload, AtomicInteger cycleCounter) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }

        try {
            Document document = Jsoup.parse(payload, properties.getRssUrl(), Parser.xmlParser());
            Elements entries = document.select("channel > item, feed > entry");
            List<NewsItemDto> items = new ArrayList<>();

            for (Element entry : entries) {
                NewsItemDto item = buildItem(entry, cycleCounter);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        } catch (Exception exception) {
            logger.error("Failed to parse CNBC feed. url: {}", properties.getRssUrl(), exception);
            return List.of();
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
}
