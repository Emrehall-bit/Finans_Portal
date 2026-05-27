package com.emrehalli.financeportal.analysis.exception;

public class DrawingNotFoundException extends RuntimeException {

    public DrawingNotFoundException(Long drawingId) {
        super("Çizim bulunamadı veya erişim yetkiniz yok: id=" + drawingId);
    }
}
