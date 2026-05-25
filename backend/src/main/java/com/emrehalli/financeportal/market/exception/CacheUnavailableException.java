package com.emrehalli.financeportal.market.exception;

/**
 * Exception thrown when cache operations are unavailable.
 */
public class CacheUnavailableException extends RuntimeException {

    public CacheUnavailableException(String message) {
        super(message);
    }

    public CacheUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}



