package com.emrehalli.financeportal.market.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TefasFundBackfillStatus {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger processedFunds = new AtomicInteger(0);
    private final AtomicInteger savedRecords = new AtomicInteger(0);
    private final AtomicInteger skippedDuplicates = new AtomicInteger(0);

    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile String lastError;

    public void start() {
        running.set(true);
        processedFunds.set(0);
        savedRecords.set(0);
        skippedDuplicates.set(0);
        startedAt = LocalDateTime.now();
        finishedAt = null;
        lastError = null;
    }

    public void finish(TefasFundHistoricalBackfillService.BackfillResult result) {
        if (result != null) {
            processedFunds.set(result.processedFunds());
            savedRecords.set(result.savedRecords());
            skippedDuplicates.set(result.skippedDuplicates());
        }
        finishedAt = LocalDateTime.now();
        running.set(false);
    }

    public void fail(String errorMessage) {
        lastError = errorMessage;
        finishedAt = LocalDateTime.now();
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getProcessedFunds() {
        return processedFunds.get();
    }

    public int getSavedRecords() {
        return savedRecords.get();
    }

    public int getSkippedDuplicates() {
        return skippedDuplicates.get();
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getLastError() {
        return lastError;
    }
}




