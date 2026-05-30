package com.emrehalli.financeportal.technicalanalysis.exception;

public class DrawingNotFoundException extends RuntimeException {

    public DrawingNotFoundException(Long drawingId) {
        super("Çizim bulunamadı veya erişim yetkiniz yok: id=" + drawingId);
    }
}

