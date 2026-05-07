package com.emrehalli.financeportal.market.domain.enums;

/**
 * Represents supported market instrument categories.
 */
public enum InstrumentType {
    FOREX,
    BOND,
    /**
     * @deprecated Legacy alias for forex pairs. Use {@link #FOREX}.
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    CURRENCY,
    /**
     * @deprecated Legacy alias for forex pairs. Use {@link #FOREX}.
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
    /**
     * Macroeconomic indicator series such as interest rates and inflation indices.
     * These are typically low-frequency (weekly/monthly) EVDS/TCMB data series
     * rather than real-time tradeable prices.
     */
    MACRO_INDICATOR,
    UNKNOWN
}
