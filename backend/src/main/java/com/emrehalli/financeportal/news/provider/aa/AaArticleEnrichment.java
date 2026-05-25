package com.emrehalli.financeportal.news.provider.aa;

public record AaArticleEnrichment(String imageUrl, String content) {
    public static final AaArticleEnrichment EMPTY = new AaArticleEnrichment(null, null);
}



