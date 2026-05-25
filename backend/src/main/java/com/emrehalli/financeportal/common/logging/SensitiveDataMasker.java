package com.emrehalli.financeportal.common.logging;

import java.util.Set;
import java.util.regex.Pattern;

public final class SensitiveDataMasker {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "authorization",
            "crumb",
            "token",
            "access_token",
            "refresh_token",
            "password",
            "api_key",
            "apikey",
            "api-key",
            "key"
    );
    private static final Set<String> LONG_QUERY_KEYS = Set.of(
            "symbols"
    );
    private static final Pattern SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(authorization|token|access_token|refresh_token|password|api[_-]?key|apikey|key)(\\s*[=:]\\s*)([^\\s,&}\"]+)"
    );

    private SensitiveDataMasker() {
    }

    public static String maskText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return SENSITIVE_KEY_VALUE_PATTERN.matcher(value).replaceAll("$1$2" + MASK);
    }

    public static String maskUri(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        try {
            int queryStart = value.indexOf('?');
            if (queryStart < 0 || queryStart == value.length() - 1) {
                return value;
            }

            int fragmentStart = value.indexOf('#', queryStart);
            String prefix = value.substring(0, queryStart + 1);
            String query = fragmentStart >= 0 ? value.substring(queryStart + 1, fragmentStart) : value.substring(queryStart + 1);
            String fragment = fragmentStart >= 0 ? value.substring(fragmentStart) : "";
            String maskedQuery = maskQueryString(query);
            return prefix + maskedQuery + fragment;
        } catch (Exception ignored) {
            return maskText(value);
        }
    }

    public static String maskQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }

        String[] parts = queryString.split("&", -1);
        for (int i = 0; i < parts.length; i++) {
            int separator = parts[i].indexOf('=');
            String key = separator >= 0 ? parts[i].substring(0, separator) : parts[i];
            String normalizedKey = key.trim().toLowerCase();
            if (SENSITIVE_QUERY_KEYS.contains(normalizedKey)) {
                parts[i] = separator >= 0 ? key + "=" + MASK : key + "=" + MASK;
            } else if (LONG_QUERY_KEYS.contains(normalizedKey)) {
                parts[i] = separator >= 0 ? key + "=[TRUNCATED]" : key + "=[TRUNCATED]";
            }
        }
        return String.join("&", parts);
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...[TRUNCATED]";
    }
}



