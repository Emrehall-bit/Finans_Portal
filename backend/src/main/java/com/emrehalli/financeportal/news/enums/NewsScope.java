package com.emrehalli.financeportal.news.enums;

import com.emrehalli.financeportal.common.exception.BadRequestException;

import java.util.Set;

public enum NewsScope {
    LOCAL(Set.of(NewsProviderType.AA_RSS, NewsProviderType.KAP)),
    GLOBAL(Set.of(NewsProviderType.CNBC_RSS)),
    ALL(Set.of(NewsProviderType.CNBC_RSS, NewsProviderType.AA_RSS, NewsProviderType.KAP));

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



