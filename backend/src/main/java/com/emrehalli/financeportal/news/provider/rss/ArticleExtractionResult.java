package com.emrehalli.financeportal.news.provider.rss;

import java.time.LocalDateTime;

public record ArticleExtractionResult(
        String title,
        String imageUrl,
        LocalDateTime publishedAt,
        String content
) {
    public static final ArticleExtractionResult EMPTY = new ArticleExtractionResult(null, null, null, null);

    public boolean hasMeaningfulData() {
        return hasText(title) || hasText(imageUrl) || publishedAt != null || hasText(content);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
