package com.emrehalli.financeportal.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ThemePreference {
    LIGHT,
    DARK,
    SYSTEM;

    @JsonCreator
    public static ThemePreference from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LIGHT" -> LIGHT;
            case "DARK" -> DARK;
            case "SYSTEM", "AUTO" -> SYSTEM;
            default -> throw new IllegalArgumentException("Invalid themePreference. Allowed values: light, dark, system");
        };
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}

