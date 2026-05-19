package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.enums.NewsProviderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReutersArticleExtractor implements ArticleExtractor {

    private static final Logger logger = LogManager.getLogger(ReutersArticleExtractor.class);
    private final RssFeedSupport rssFeedSupport;

    public ReutersArticleExtractor(RssFeedSupport rssFeedSupport) {
        this.rssFeedSupport = rssFeedSupport;
    }

    @Override
    public boolean supports(NewsProviderType providerType) {
        return providerType == NewsProviderType.REUTERS_RSS;
    }

    @Override
    public ArticleExtractionResult extract(Document document, String baseUrl) {
        if (document == null) {
            return ArticleExtractionResult.EMPTY;
        }

        Document working = document.clone();
        working.select("script, style, nav, footer, aside, noscript, [data-testid*=Related], [data-testid*=Newsletter], [data-testid*=ad]").remove();

        String title = rssFeedSupport.firstNonBlank(
                attr(working, "meta[property=og:title]", "content"),
                text(working, "h1[data-testid=Heading], h1")
        );
        String imageUrl = rssFeedSupport.firstNonBlank(
                attr(working, "meta[property=og:image]", "content"),
                attr(working, "meta[name=twitter:image]", "content")
        );
        LocalDateTime publishedAt = rssFeedSupport.parsePublishedAt(
                rssFeedSupport.firstNonBlank(
                        attr(working, "meta[property=article:published_time]", "content"),
                        attr(working, "meta[name=article:published_time]", "content"),
                        attr(working, "time[datetime]", "datetime")
                ),
                logger,
                "Reuters article"
        );

        Element body = working.selectFirst("[data-testid=paragraph-0], [data-testid=Body], article");
        if (body == null) {
            return new ArticleExtractionResult(title, imageUrl, publishedAt, null);
        }

        List<String> parts = new ArrayList<>();
        for (Element element : body.select("h2, p, li")) {
            String value = rssFeedSupport.clean(element.text());
            if (value == null || value.length() < 30) {
                continue;
            }
            parts.add("h2".equals(element.tagName()) ? "## " + value : value);
        }

        return new ArticleExtractionResult(title, imageUrl, publishedAt, parts.isEmpty() ? null : String.join("\n\n", parts));
    }

    private String attr(Document document, String selector, String attr) {
        Element element = document.selectFirst(selector);
        return element == null ? null : rssFeedSupport.clean(element.attr(attr));
    }

    private String text(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? null : rssFeedSupport.clean(element.text());
    }
}
