package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.enums.NewsProviderType;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericArticleExtractorTest {

    private final GenericArticleExtractor extractor = new GenericArticleExtractor(new RssFeedSupport());

    @Test
    void extractsMeaningfulArticleBody() {
        String html = """
                <html>
                  <head>
                    <title>Oil prices rise on supply concerns</title>
                    <meta property="article:published_time" content="2026-05-20T10:15:00Z" />
                    <meta property="og:image" content="https://cdn.example.com/image.jpg" />
                  </head>
                  <body>
                    <nav>ignore</nav>
                    <article>
                      <h2>Market context</h2>
                      <p>Oil prices gained more than two percent as traders assessed supply risks across the Middle East and tighter inventory expectations for the summer season.</p>
                      <p>Airlines and refiners are watching higher fuel costs closely because sustained moves in Brent can quickly change margins across transport and industrial sectors.</p>
                    </article>
                  </body>
                </html>
                """;

        ArticleExtractionResult result = extractor.extract(Jsoup.parse(html, "https://example.com/story"), "https://example.com/story");

        assertEquals("Oil prices rise on supply concerns", result.title());
        assertEquals("https://cdn.example.com/image.jpg", result.imageUrl());
        assertNotNull(result.publishedAt());
        assertTrue(result.content().contains("Market context"));
        assertTrue(result.content().contains("Oil prices gained more than two percent"));
    }

    @Test
    void supportsAllProviders() {
        assertTrue(extractor.supports(NewsProviderType.CNBC_RSS));
        assertTrue(extractor.supports(NewsProviderType.REUTERS_RSS));
    }
}
