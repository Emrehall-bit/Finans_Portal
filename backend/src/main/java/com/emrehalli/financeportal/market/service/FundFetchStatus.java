package com.emrehalli.financeportal.market.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FundFetchStatus {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger processedFunds = new AtomicInteger(0);
    private final AtomicInteger savedFunds = new AtomicInteger(0);

    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile String lastError;

    public void start() {
        running.set(true);
        processedFunds.set(0);
        savedFunds.set(0);
        startedAt = LocalDateTime.now();
        finishedAt = null;
        lastError = null;
    }

    public void finish(int processedFundsCount, int savedFundsCount) {
        processedFunds.set(processedFundsCount);
        savedFunds.set(savedFundsCount);
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

    public int getSavedFunds() {
        return savedFunds.get();
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




