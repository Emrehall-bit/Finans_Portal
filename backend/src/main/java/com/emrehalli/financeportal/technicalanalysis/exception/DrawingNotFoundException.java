package com.emrehalli.financeportal.technicalanalysis.exception;

public class DrawingNotFoundException extends RuntimeException {

    public DrawingNotFoundException(Long drawingId) {
        super("Ã‡izim bulunamadÄ± veya eriÅŸim yetkiniz yok: id=" + drawingId);
    }
}

