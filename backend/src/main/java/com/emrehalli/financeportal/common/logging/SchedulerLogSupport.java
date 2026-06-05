package com.emrehalli.financeportal.common.logging;

import java.time.Duration;
import java.time.Instant;

public final class SchedulerLogSupport {

    private SchedulerLogSupport() {
    }

    public static Run start(String schedulerName) {
        return new Run(schedulerName, Instant.now());
    }

    public record Run(String schedulerName, Instant startedAt) {

        public void log(org.slf4j.Logger logger, int processedCount, int successCount, int failedCount) {
            log(logger, processedCount, successCount, failedCount, null);
        }

        public void log(org.slf4j.Logger logger, int processedCount, int successCount, int failedCount, Throwable throwable) {
            Instant finishedAt = Instant.now();
            long durationMs = Duration.between(startedAt, finishedAt).toMillis();

            try (StructuredLogContext ignored = StructuredLogContext.open()
                    .put(LoggingConstants.LOG_TYPE_KEY, "scheduler")
                    .put(LoggingConstants.SCHEDULER_NAME_KEY, schedulerName)
                    .putInstant(LoggingConstants.STARTED_AT_KEY, startedAt)
                    .putInstant(LoggingConstants.FINISHED_AT_KEY, finishedAt)
                    .put(LoggingConstants.DURATION_MS_KEY, durationMs)
                    .put(LoggingConstants.PROCESSED_COUNT_KEY, processedCount)
                    .put(LoggingConstants.SUCCESS_COUNT_KEY, successCount)
                    .put(LoggingConstants.FAILED_COUNT_KEY, failedCount)
                    .put(LoggingConstants.SUCCESS_KEY, failedCount == 0)) {
                if (throwable == null && failedCount == 0) {
                    logger.info("Scheduler completed");
                } else if (throwable == null) {
                    logger.warn("Scheduler completed with failures");
                } else {
                    logger.error("Scheduler failed", throwable);
                }
            }
        }

        public void log(org.apache.logging.log4j.Logger logger, int processedCount, int successCount, int failedCount) {
            log(logger, processedCount, successCount, failedCount, null);
        }

        public void log(org.apache.logging.log4j.Logger logger, int processedCount, int successCount, int failedCount, Throwable throwable) {
            Instant finishedAt = Instant.now();
            long durationMs = Duration.between(startedAt, finishedAt).toMillis();

            try (StructuredLogContext ignored = StructuredLogContext.open()
                    .put(LoggingConstants.LOG_TYPE_KEY, "scheduler")
                    .put(LoggingConstants.SCHEDULER_NAME_KEY, schedulerName)
                    .putInstant(LoggingConstants.STARTED_AT_KEY, startedAt)
                    .putInstant(LoggingConstants.FINISHED_AT_KEY, finishedAt)
                    .put(LoggingConstants.DURATION_MS_KEY, durationMs)
                    .put(LoggingConstants.PROCESSED_COUNT_KEY, processedCount)
                    .put(LoggingConstants.SUCCESS_COUNT_KEY, successCount)
                    .put(LoggingConstants.FAILED_COUNT_KEY, failedCount)
                    .put(LoggingConstants.SUCCESS_KEY, failedCount == 0)) {
                if (throwable == null && failedCount == 0) {
                    logger.info("Scheduler completed");
                } else if (throwable == null) {
                    logger.warn("Scheduler completed with failures");
                } else {
                    logger.error("Scheduler failed", throwable);
                }
            }
        }
    }
}




