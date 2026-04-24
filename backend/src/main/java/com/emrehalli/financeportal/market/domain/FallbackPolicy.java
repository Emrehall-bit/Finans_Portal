package com.emrehalli.financeportal.market.domain;

public enum FallbackPolicy {
    RETURN_STALE_IF_AVAILABLE,
    RETURN_EMPTY,
    FAIL_FAST
}