package com.emrehalli.financeportal.common.exception;

public class ProviderRateLimitException extends RuntimeException {

    public ProviderRateLimitException(String provider, String endpoint) {
        super("Provider rate limited: " + provider + " (" + endpoint + ")");
    }
}
