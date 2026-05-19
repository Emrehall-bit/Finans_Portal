package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.enums.NewsProviderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class GenericArticleExtractor implements ArticleExtractor {

    private static final Logger logger = LogManager.getLogger(GenericArticleExtractor.class);
    private static final int MIN_PARAGRAPH_LENGTH = 35;
    private static final int MIN_HEADING_LENGTH = 12;

    private final RssFeedSupport rssFeedSupport;

    public GenericArticleExtractor(RssFeedSupport rssFeedSupport) {
        this.rssFeedSupport = rssFeedSupport;
    }

    @Override
    public boolean supports(NewsProviderType providerType) {
        return true;
    }

    @Override
    public ArticleExtractionResult extract(Document document, String baseUrl) {
        if (document == null) {
            return ArticleExtractionResult.EMPTY;
        }

        Document working = document.clone();
        cleanNoise(working);

        String title = rssFeedSupport.firstNonBlank(
                rssFeedSupport.clean(working.title()),
                textOf(working, "meta[property=og:title]", "content"),
                textOf(working, "meta[name=twitter:title]", "content"),
                textOf(working, "h1", null)
        );
        String imageUrl = rssFeedSupport.firstNonBlank(
                textOf(working, "meta[property=og:image]", "content"),
                textOf(working, "meta[name=twitter:image]", "content")
        );
        LocalDateTime publishedAt = extractPublishedAt(working);
        String content = extractContent(working);

        return new ArticleExtractionResult(title, imageUrl, publishedAt, content);
    }

    private void cleanNoise(Document document) {
        document.select("script, style, nav, footer, aside, noscript, iframe, svg, form, button").remove();
        document.select("[role=banner], [role=navigation], [role=complementary], [role=contentinfo]").remove();
        document.select(
                ".ad, .ads, .advertisement, .newsletter, .subscribe, .paywall, .social, .share, .related, " +
                        ".related-articles, .recommended, .breadcrumb, .live-updates, .video, .promo"
        ).remove();
    }

    private LocalDateTime extractPublishedAt(Document document) {
        String rawDate = rssFeedSupport.firstNonBlank(
                textOf(document, "meta[property=article:published_time]", "content"),
                textOf(document, "meta[name=article:published_time]", "content"),
                textOf(document, "meta[name=parsely-pub-date]", "content"),
                textOf(document, "meta[name=pubdate]", "content"),
                textOf(document, "time[datetime]", "datetime")
        );
        return rssFeedSupport.parsePublishedAt(rawDate, logger, "article-page");
    }

    private String extractContent(Document document) {
        List<Element> candidates = new ArrayList<>();
        candidates.addAll(document.select("article"));
        candidates.addAll(document.select("main article, main, [itemprop=articleBody], .article-body, .ArticleBody-articleBody, .story-body, .StandardArticleBody_body"));

        Element best = candidates.stream()
                .distinct()
                .max(Comparator.comparingInt(this::scoreCandidate))
                .orElseGet(() -> document.body());
        if (best == null) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        for (Element element : best.select("h2, h3, p, li")) {
            String text = rssFeedSupport.clean(element.text());
            if (text == null) {
                continue;
            }
            if (("h2".equals(element.tagName()) || "h3".equals(element.tagName()))
                    && text.length() < MIN_HEADING_LENGTH) {
                continue;
            }
            if (!"h2".equals(element.tagName()) && !"h3".equals(element.tagName())
                    && text.length() < MIN_PARAGRAPH_LENGTH) {
                continue;
            }
            if (isNoiseText(text)) {
                continue;
            }
            if ("h2".equals(element.tagName()) || "h3".equals(element.tagName())) {
                parts.add("## " + text);
                continue;
            }
            parts.add(text);
        }

        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n\n", parts);
    }

    private int scoreCandidate(Element element) {
        return element.select("p").text().length() + element.select("li").text().length() / 2;
    }

    private String textOf(Document document, String selector, String attr) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        if (attr == null) {
            return rssFeedSupport.clean(element.text());
        }
        return rssFeedSupport.clean(element.attr(attr));
    }

    private boolean isNoiseText(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("advertisement")
                || normalized.contains("newsletter")
                || normalized.contains("sign up")
                || normalized.contains("subscribe")
                || normalized.contains("read more")
                || normalized.contains("click here")
                || normalized.contains("watch live");
    }
}
