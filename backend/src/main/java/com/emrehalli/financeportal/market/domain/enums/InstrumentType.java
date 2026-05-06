package com.emrehalli.financeportal.market.domain.enums;

public enum InstrumentType {
    CURRENCY,
    /**
     * @deprecated Legacy alias for currency pairs. Use {@link #CURRENCY}.
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    FX,
    /**
     * @deprecated Legacy alias for precious metals. Use {@link #COMMODITY}.
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    GOLD,
    STOCK,
    FUND,
    VIOP,
    IPO,
    /**
     * @deprecated Legacy crypto type from the provider-based market module.
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    CRYPTO,
    /**
     * @deprecated Legacy index type from the provider-based market module.
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    INDEX,
    COMMODITY,
    UNKNOWN
}
