package com.emrehalli.financeportal.technicalanalysis.exception;

import org.springframework.http.HttpStatus;

public class TechnicalAnalysisValidationException extends TechnicalAnalysisException {

    public TechnicalAnalysisValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
