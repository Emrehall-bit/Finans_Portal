package com.emrehalli.financeportal.market.exception;

public class MarketDataNotFoundException extends RuntimeException {

    public MarketDataNotFoundException(String message) {
        super(message);
    }
}