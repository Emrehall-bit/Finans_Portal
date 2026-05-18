package com.emrehalli.financeportal.news.provider.aa.client;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.aa.AaArticleEnricher;
import com.emrehalli.financeportal.news.provider.aa.AaArticleEnrichment;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
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

@Component
public class AaRssNewsClient {

    private static final Logger logger = LogManager.getLogger(AaRssNewsClient.class);

    private final RestTemplate restTemplate;
    private final AaNewsProperties properties;
    private final RssFeedSupport rssFeedSupport;
    private final AaArticleEnricher aaArticleEnricher;

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

    public List<NewsItemDto> fetchEconomyNews() {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    properties.getRssUrl(),
                    HttpMethod.GET,
                    null,
                    byte[].class
            );
            String payload = decodeResponseBody(response);
            if (payload == null || payload.isBlank()) {
                logger.warn("AA RSS response was empty");
                return List.of();
            }

            AtomicInteger cycleCounter = aaArticleEnricher.newCycleCounter();
            List<NewsItemDto> items = parse(payload, cycleCounter);
            logger.info("AA feed parsed. url: {}, fetched: {}, articleFetches: {}",
                    properties.getRssUrl(), items.size(), cycleCounter.get());
            return items;
        } catch (RestClientException e) {
            logger.error("AA RSS fetch failed. url: {}", properties.getRssUrl(), e);
            return List.of();
        } catch (Exception e) {
            logger.error("Unexpected AA RSS fetch failure. url: {}", properties.getRssUrl(), e);
            return List.of();
        }
    }

    String decodeResponseBody(ResponseEntity<byte[]> response) {
        return rssFeedSupport.decodeResponseBody(response, logger, "AA RSS");
    }

    List<NewsItemDto> parse(String payload, AtomicInteger cycleCounter) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }

        try {
            List<NewsItemDto> xmlItems = parseXmlFeed(payload, cycleCounter);
            if (!xmlItems.isEmpty()) {
                return xmlItems;
            }

            List<NewsItemDto> htmlItems = parseHtmlFeed(payload);
            if (!htmlItems.isEmpty()) {
                logger.info("AA feed fallback parser used. url: {}, fetched: {}", properties.getRssUrl(), htmlItems.size());
                return htmlItems;
            }

            logger.warn("AA feed did not contain parsable items. url: {}", properties.getRssUrl());
            return List.of();
        } catch (Exception e) {
            logger.error("Failed to parse AA feed. url: {}", properties.getRssUrl(), e);
            return List.of();
        }
    }

    List<NewsItemDto> parse(String payload) {
        return parse(payload, new AtomicInteger(0));
    }

    private List<NewsItemDto> parseXmlFeed(String xml, AtomicInteger cycleCounter) {
        Document document = Jsoup.parse(xml, properties.getRssUrl(), Parser.xmlParser());
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

            // Single page fetch: extracts both og:image and full article content
            AaArticleEnrichment enrichment = aaArticleEnricher.enrich(link, cycleCounter);

            // Image: prefer in-feed media/enclosure, fall back to article og:image
            String rssImageUrl = rssFeedSupport.resolveImageUrl(entry, descriptionHtml);
            String imageUrl = rssImageUrl != null ? rssImageUrl : enrichment.imageUrl();

            // Summary: use full article content when meaningfully longer than RSS snippet
            String summary = selectBestSummary(rssSummary, enrichment.content());

            if (enrichment.content() != null) {
                logger.debug("AA article content enriched. url: {}, rssSummaryLen: {}, contentLen: {}",
                        link,
                        rssSummary != null ? rssSummary.length() : 0,
                        enrichment.content().length());
            }

            NewsItemDto item = buildItem(title, link, guid, summary, imageUrl, pubDate);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    private List<NewsItemDto> parseHtmlFeed(String html) {
        Document document = Jsoup.parse(html, properties.getRssUrl());
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

            NewsItemDto item = buildItem(title, link, null, null, null, null);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    private NewsItemDto buildItem(String title, String link, String guid, String summary, String imageUrl, String pubDate) {
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
                .category(properties.getDefaultCategory())
                .relatedSymbol(null)
                .url(link)
                .imageUrl(imageUrl)
                .publishedAt(rssFeedSupport.parsePublishedAt(pubDate, logger, "AA RSS"))
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
        // Use full article content only when it is substantial (>=500 chars) AND meaningfully longer
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
}
