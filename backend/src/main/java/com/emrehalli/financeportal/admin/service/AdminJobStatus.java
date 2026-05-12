package com.emrehalli.financeportal.admin.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminJobStatus {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);

    public void start(int total) {
        this.total.set(total);
        this.processed.set(0);
        this.running.set(true);
    }

    public void incrementProcessed() {
        this.processed.incrementAndGet();
    }

    public void finish() {
        this.running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getProcessed() {
        return processed.get();
    }

    public int getTotal() {
        return total.get();
    }
}
