package com.emrehalli.financeportal.market.domain.enums;

/**
 * Supported market price history intervals.
 */
public enum IntervalType {
    ONE_HOUR("1h"),
    FOUR_HOURS("4h"),
    ONE_DAY("1d"),
    ONE_WEEK("1w");

    private final String value;

    IntervalType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}




