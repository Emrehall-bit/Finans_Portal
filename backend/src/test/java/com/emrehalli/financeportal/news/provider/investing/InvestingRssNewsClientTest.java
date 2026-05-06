package com.emrehalli.financeportal.news.provider.investing;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestingRssNewsClientTest {

    private final InvestingRssNewsClient client =
            new InvestingRssNewsClient(new RestTemplate(), properties(), new RssFeedSupport());

    @Test
    void parsesRssItemsIntoNewsDtos() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Global growth outlook improves</title>
                      <link>https://www.investing.com/news/economy/global-growth-outlook-123</link>
                      <guid>investing-guid-123</guid>
                      <description>Markets reacted to the latest macro data.</description>
                      <pubDate>Sat, 25 Apr 2026 10:30:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getExternalId()).isEqualTo("investing-guid-123");
            assertThat(item.getTitle()).isEqualTo("Global growth outlook improves");
            assertThat(item.getUrl()).isEqualTo("https://www.investing.com/news/economy/global-growth-outlook-123");
            assertThat(item.getSummary()).isEqualTo("Markets reacted to the latest macro data.");
            assertThat(item.getSource()).isEqualTo("Investing.com");
            assertThat(item.getProvider()).isEqualTo("INVESTING_RSS");
            assertThat(item.getCategory()).isEqualTo("ECONOMY");
            assertThat(item.getLanguage()).isEqualTo("en");
            assertThat(item.getRegionScope()).isEqualTo("GLOBAL");
            assertThat(item.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void usesLinkHashWhenGuidIsMissing() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Rates stay in focus</title>
                      <link>https://www.investing.com/news/economy/rates-stay-in-focus-456</link>
                      <description>Analysts expect volatility.</description>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getExternalId()).startsWith("INVESTING_RSS-")
        );
    }

    @Test
    void fallsBackToDescriptionImageWhenMetadataIsMissing() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Rates stay in focus</title>
                      <link>https://www.investing.com/news/economy/rates-stay-in-focus-456</link>
                      <description><![CDATA[<img src="https://cdn.example.com/investing-description.jpg" />Analysts expect volatility.]]></description>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getImageUrl()).isEqualTo("https://cdn.example.com/investing-description.jpg");
            assertThat(item.getSummary()).isEqualTo("Analysts expect volatility.");
        });
    }

    @Test
    void preservesTurkishCharactersWhenParsingUtf8Payload() {
        List<NewsItemDto> items = client.parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>T\u00fcrkiye ekonomisinde b\u00fcy\u00fcme beklentisi g\u00fc\u00e7lendi</title>
                      <link>https://www.investing.com/news/economy/turkiye-ekonomisi-789</link>
                      <description>Enflasyon ve ihracat g\u00f6stergeleri olumlu sinyal verdi.</description>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).contains("T\u00fcrkiye", "b\u00fcy\u00fcme", "g\u00fc\u00e7lendi");
            assertThat(item.getSummary()).contains("Enflasyon", "g\u00f6stergeleri", "olumlu");
        });
    }

    @Test
    void parsesPlainTimestampDateFormat() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Plain timestamp sample</title>
                      <link>https://www.investing.com/news/economy/plain-timestamp-999</link>
                      <description>Summary</description>
                      <pubDate>2026-05-03 15:42:50</pubDate>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getPublishedAt()).isNotNull()
        );
    }

    private InvestingNewsProperties properties() {
        InvestingNewsProperties properties = new InvestingNewsProperties();
        properties.setEnabled(true);
        properties.setRssUrl("https://www.investing.com/rss/news_14.rss");
        properties.setDefaultCategory("ECONOMY");
        properties.setDefaultLanguage("en");
        properties.setDefaultRegionScope("GLOBAL");
        return properties;
    }
}
