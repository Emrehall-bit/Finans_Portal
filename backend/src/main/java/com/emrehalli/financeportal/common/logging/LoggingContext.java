package com.emrehalli.financeportal.common.logging;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.MDC;

public final class LoggingContext {

    private LoggingContext() {
    }

    public static void put(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            remove(key);
            return;
        }
        MDC.put(key, value);
        ThreadContext.put(key, value);
    }

    public static String get(String key) {
        String value = MDC.get(key);
        return value != null ? value : ThreadContext.get(key);
    }

    public static void remove(String key) {
        if (key == null) {
            return;
        }
        MDC.remove(key);
        ThreadContext.remove(key);
    }

    public static void clear() {
        MDC.clear();
        ThreadContext.clearAll();
    }
}
