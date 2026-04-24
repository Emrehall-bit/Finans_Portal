package com.emrehalli.financeportal.news.enums;

import com.emrehalli.financeportal.common.exception.BadRequestException;

public enum NewsProviderType {
    FINNHUB,
    BLOOMBERG_HT,
    AA_RSS;

    public static NewsProviderType from(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("provider cannot be blank");
        }

        try {
            return NewsProviderType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid provider. Allowed values: FINNHUB, BLOOMBERG_HT, AA_RSS");
        }
    }
}



