package com.emrehalli.financeportal.news.enums;

import com.emrehalli.financeportal.common.exception.BadRequestException;

import java.util.Set;

public enum NewsScope {
    LOCAL(Set.of(NewsProviderType.BLOOMBERG_HT, NewsProviderType.AA_RSS)),
    GLOBAL(Set.of(NewsProviderType.FINNHUB)),
    ALL(Set.of(NewsProviderType.FINNHUB, NewsProviderType.BLOOMBERG_HT, NewsProviderType.AA_RSS));

    private final Set<NewsProviderType> providers;

    NewsScope(Set<NewsProviderType> providers) {
        this.providers = providers;
    }

    public Set<NewsProviderType> providers() {
        return providers;
    }

    public static NewsScope from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        try {
            return NewsScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid scope. Allowed values: local, global, all");
        }
    }
}



