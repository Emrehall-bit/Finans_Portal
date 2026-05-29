package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.enums.NewsProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssProviderRelevanceFilterTest {

    private final RssProviderRelevanceFilter filter = new RssProviderRelevanceFilter();

    @Test
    void keepsFinanceAndMacroStories() {
        boolean relevant = filter.isRelevant(
                NewsProviderType.CNBC_RSS,
                "Fed signals markets may face more rate volatility",
                "Stocks, bonds and the dollar reacted after the latest inflation print.",
                "Markets",
                "https://example.com/news/fed-rates"
        );

        assertTrue(relevant);
    }

    @Test
    void dropsClearlyIrrelevantLifestyleStories() {
        boolean relevant = filter.isRelevant(
                NewsProviderType.CNBC_RSS,
                "Celebrity travel guide for summer beaches",
                "Lifestyle editors shared fashion and entertainment picks for the holiday season.",
                "Lifestyle",
                "https://example.com/lifestyle/beach-guide"
        );

        assertFalse(relevant);
    }

    @Test
    void keepsFinanceStoriesForCnbcFilter() {
        boolean relevant = filter.isRelevant(
                NewsProviderType.CNBC_RSS,
                "Nvidia lifts stocks as Treasury yields ease after inflation data",
                "Markets tracked AI and semiconductor winners while traders watched Fed rate expectations.",
                "Markets",
                "https://www.reuters.com/world/us/nvidia-yields-test"
        );

        assertTrue(relevant);
    }
}




