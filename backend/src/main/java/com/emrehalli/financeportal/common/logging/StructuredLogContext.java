package com.emrehalli.financeportal.common.logging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructuredLogContext implements AutoCloseable {

    private final Map<String, String> previousValues = new LinkedHashMap<>();

    private StructuredLogContext() {
    }

    public static StructuredLogContext open() {
        return new StructuredLogContext();
    }

    public StructuredLogContext put(String key, Object value) {
        if (key == null) {
            return this;
        }

        previousValues.putIfAbsent(key, LoggingContext.get(key));
        if (value == null) {
            LoggingContext.remove(key);
        } else {
            LoggingContext.put(key, String.valueOf(value));
        }
        return this;
    }

    public StructuredLogContext putInstant(String key, Instant value) {
        return put(key, value == null ? null : value.toString());
    }

    @Override
    public void close() {
        previousValues.forEach((key, value) -> {
            if (value == null) {
                LoggingContext.remove(key);
            } else {
                LoggingContext.put(key, value);
            }
        });
    }
}




