package com.emrehalli.financeportal.technicalanalysis.exception;

import org.springframework.http.HttpStatus;

public class TechnicalAnalysisException extends RuntimeException {

    private final HttpStatus status;

    public TechnicalAnalysisException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public TechnicalAnalysisException(String message, HttpStatus status) {
        super(message);
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
    }

    public HttpStatus status() {
        return status;
    }
}
