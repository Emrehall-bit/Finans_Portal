package com.emrehalli.financeportal.technicalanalysis.exception;

public class PremiumRequiredException extends RuntimeException {

    public PremiumRequiredException(String message) {
        super(message);
    }

    public PremiumRequiredException() {
        super("Bu özellik için Premium üyelik gereklidir");
    }
}

