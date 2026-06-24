package com.emrehalli.financeportal.news.enums;

import com.emrehalli.financeportal.common.exception.BadRequestException;

public enum NewsProviderType {
    AA_RSS,
    CNBC_RSS,
    GUARDIAN,
    KAP,
    SYSTEM_GENERATED;

    public static NewsProviderType from(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("provider cannot be blank");
        }

        try {
            return NewsProviderType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid provider. Allowed values: AA_RSS, CNBC_RSS, GUARDIAN, KAP, SYSTEM_GENERATED");
        }
    }
}

