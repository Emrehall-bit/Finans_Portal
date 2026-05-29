package com.emrehalli.financeportal.market.exception;

/**
 * Exception thrown when a market instrument cannot be found.
 */
public class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException(String message) {
        super(message);
    }

    public InstrumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}




