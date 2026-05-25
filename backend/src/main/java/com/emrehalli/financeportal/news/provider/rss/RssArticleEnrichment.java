package com.emrehalli.financeportal.news.provider.rss;

import java.time.LocalDateTime;

public record RssArticleEnrichment(
        String title,
        String imageUrl,
        LocalDateTime publishedAt,
        String content,
        String qualityStatus,
        LocalDateTime contentEnrichedAt
) {
    public static final RssArticleEnrichment EMPTY = new RssArticleEnrichment(null, null, null, null, null, null);

    public boolean hasEnrichment() {
        return hasText(title)
                || hasText(imageUrl)
                || publishedAt != null
                || hasText(content)
                || hasText(qualityStatus)
                || contentEnrichedAt != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}



