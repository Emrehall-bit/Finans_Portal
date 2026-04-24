package com.emrehalli.financeportal.market.provider.binance.dto;

public record BinanceKlineResponse(
        Long openTime,
        String open,
        String high,
        String low,
        String close,
        String volume,
        Long closeTime
) {
    public static BinanceKlineResponse fromArray(Object[] values) {
        if (values == null || values.length < 7) {
            return null;
        }

        return new BinanceKlineResponse(
                toLong(values[0]),
                toStringValue(values[1]),
                toStringValue(values[2]),
                toStringValue(values[3]),
                toStringValue(values[4]),
                toStringValue(values[5]),
                toLong(values[6])
        );
    }

    private static String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
