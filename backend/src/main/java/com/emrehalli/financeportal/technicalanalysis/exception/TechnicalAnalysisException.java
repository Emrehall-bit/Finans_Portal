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

    public static class Validation extends TechnicalAnalysisException {

        public Validation(String message) {
            super(message, HttpStatus.BAD_REQUEST);
        }
    }

    public static class NotFound extends TechnicalAnalysisException {

        public NotFound(String message) {
            super(message, HttpStatus.NOT_FOUND);
        }
    }

    public static class DrawingNotFoundException extends RuntimeException {

        public DrawingNotFoundException(Long drawingId) {
            super("Çizim bulunamadı veya erişim yetkiniz yok: id=" + drawingId);
        }
    }

    public static class PremiumRequiredException extends RuntimeException {

        public PremiumRequiredException(String message) {
            super(message);
        }

        public PremiumRequiredException() {
            super("Bu ozellik icin Premium uyelik gereklidir");
        }
    }
}
