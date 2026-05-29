package com.emrehalli.financeportal.news.provider.rss;

import java.time.LocalDateTime;

public record ArticleExtractionResult(
        String title,
        String fullContent,
        String imageUrl,
        LocalDateTime publishedAt,
        String author,
        int contentLength,
        String extractionStatus,
        String errorMessage
) {
    public static final ArticleExtractionResult EMPTY = new ArticleExtractionResult(null, null, null, null, null, 0, "EMPTY", null);

    public boolean hasMeaningfulData() {
        return hasText(title) || hasText(imageUrl) || publishedAt != null || hasText(fullContent);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}




