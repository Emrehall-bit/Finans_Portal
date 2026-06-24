package com.emrehalli.financeportal.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PreferredLanguage {
    TR,
    EN;

    @JsonCreator
    public static PreferredLanguage from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TR", "TURKCE", "TURKISH" -> TR;
            case "EN", "ENGLISH" -> EN;
            default -> throw new IllegalArgumentException("Invalid preferredLanguage. Allowed values: tr, en");
        };
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}

