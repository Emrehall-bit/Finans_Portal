package com.emrehalli.financeportal.news.provider.investing;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.rss.ArticleImageFetcher;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InvestingRssNewsClient {

    private static final Logger logger = LogManager.getLogger(InvestingRssNewsClient.class);
    private static final String INVESTING_LANGUAGE = "en";

    private final RestTemplate restTemplate;
    private final InvestingNewsProperties properties;
    private final RssFeedSupport rssFeedSupport;
    private final ArticleImageFetcher articleImageFetcher;

    public InvestingRssNewsClient(
            RestTemplate restTemplate,
            InvestingNewsProperties properties,
            RssFeedSupport rssFeedSupport,
            ArticleImageFetcher articleImageFetcher
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.rssFeedSupport = rssFeedSupport;
        this.articleImageFetcher = articleImageFetcher;
    }

    public List<NewsItemDto> fetchNews() {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    properties.getRssUrl(),
                    HttpMethod.GET,
                    null,
                    byte[].class
            );
            String payload = rssFeedSupport.decodeResponseBody(response, logger, "Investing RSS");
            if (payload == null || payload.isBlank()) {
                logger.warn("Investing RSS response was empty");
                return List.of();
            }

            AtomicInteger cycleCounter = articleImageFetcher.newCycleCounter();
            List<NewsItemDto> items = parse(payload, cycleCounter);
            logger.info("Investing feed parsed. url: {}, fetched: {}, imageFetches: {}", properties.getRssUrl(), items.size(), cycleCounter.get());
            return items;
        } catch (RestClientException e) {
            logger.error("Investing RSS fetch failed. url: {}", properties.getRssUrl(), e);
            return List.of();
        } catch (Exception e) {
            logger.error("Unexpected Investing RSS fetch failure. url: {}", properties.getRssUrl(), e);
            return List.of();
        }
    }

    List<NewsItemDto> parse(String payload) {
        return parse(payload, new AtomicInteger(0));
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
                String title = rssFeedSupport.text(entry, "title");
                String link = rssFeedSupport.resolveXmlLink(entry);
                String guid = rssFeedSupport.firstNonBlank(
                        rssFeedSupport.text(entry, "guid"),
                        rssFeedSupport.text(entry, "id")
                );
                String descriptionHtml = rssFeedSupport.html(entry, "description");
                String summary = rssFeedSupport.firstNonBlank(
                        rssFeedSupport.extractTextFromHtml(descriptionHtml),
                        rssFeedSupport.text(entry, "description"),
                        rssFeedSupport.text(entry, "summary"),
                        rssFeedSupport.text(entry, "content|encoded"),
                        rssFeedSupport.text(entry, "content\\:encoded"),
                        rssFeedSupport.text(entry, "content")
                );
                String imageUrl = resolveImageUrl(entry, descriptionHtml, link, cycleCounter);
                String pubDate = rssFeedSupport.firstNonBlank(
                        rssFeedSupport.text(entry, "pubDate"),
                        rssFeedSupport.text(entry, "dc|date"),
                        rssFeedSupport.text(entry, "dc\\:date"),
                        rssFeedSupport.text(entry, "updated"),
                        rssFeedSupport.text(entry, "published")
                );

                NewsItemDto item = buildItem(title, link, guid, summary, imageUrl, pubDate);
                if (item != null) {
                    items.add(item);
                }
            }

            return items;
        } catch (Exception e) {
            logger.error("Failed to parse Investing feed. url: {}", properties.getRssUrl(), e);
            return List.of();
        }
    }

    private NewsItemDto buildItem(String title, String link, String guid, String summary, String imageUrl, String pubDate) {
        if (title == null || link == null) {
            return null;
        }

        String normalizedSummary = normalizeSummary(summary);

        return NewsItemDto.builder()
                .externalId(rssFeedSupport.resolveExternalId(NewsProviderType.INVESTING_RSS.name(), guid, link))
                .title(title)
                .summary(normalizedSummary)
                .source("Investing.com")
                .provider(NewsProviderType.INVESTING_RSS.name())
                .language(INVESTING_LANGUAGE)
                .regionScope(properties.getDefaultRegionScope())
                .category(properties.getDefaultCategory())
                .relatedSymbol(null)
                .url(link)
                .imageUrl(imageUrl)
                .publishedAt(rssFeedSupport.parsePublishedAt(pubDate, logger, "Investing RSS"))
                .build();
    }

    private String resolveImageUrl(Element entry, String descriptionHtml, String link, AtomicInteger cycleCounter) {
        String imageUrl = rssFeedSupport.resolveImageUrl(entry, descriptionHtml);
        if (imageUrl != null || link == null || link.isBlank()) {
            return imageUrl;
        }
        return articleImageFetcher.fetchImageUrl(link, cycleCounter, "Investing");
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
