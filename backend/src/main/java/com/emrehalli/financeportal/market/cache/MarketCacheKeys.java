package com.emrehalli.financeportal.market.cache;

public final class MarketCacheKeys {

    private MarketCacheKeys() {
    }

    public static final String ALL_QUOTES = "market:quotes:all";

    public static String quotesBySource(String source) {
        return "market:quotes:source:" + source;
    }

    public static String quoteBySymbol(String symbol) {
        return "market:quotes:symbol:" + symbol;
    }
}
