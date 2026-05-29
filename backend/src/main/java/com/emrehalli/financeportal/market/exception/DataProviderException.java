package com.emrehalli.financeportal.market.exception;

/**
 * Exception thrown for market data provider failures.
 */
public class DataProviderException extends RuntimeException {

    public DataProviderException(String message) {
        super(message);
    }

    public DataProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}




