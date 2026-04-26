package com.emrehalli.financeportal.technicalanalysis.exception;

import org.springframework.http.HttpStatus;

public class TechnicalAnalysisNotFoundException extends TechnicalAnalysisException {

    public TechnicalAnalysisNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
