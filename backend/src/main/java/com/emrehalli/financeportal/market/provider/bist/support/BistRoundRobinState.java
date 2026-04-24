package com.emrehalli.financeportal.market.provider.bist.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class BistRoundRobinState {

    private final AtomicInteger cursor = new AtomicInteger(0);
    private final AtomicReference<Instant> rateLimitedUntil = new AtomicReference<>(Instant.EPOCH);

    public BatchSelection nextBatch(List<String> symbols, int batchSize) {
        if (symbols == null || symbols.isEmpty()) {
            return new BatchSelection(0, List.of());
        }

        int safeBatchSize = Math.max(batchSize, 1);
        int startIndex = Math.floorMod(cursor.get(), symbols.size());
        int endIndex = Math.min(startIndex + safeBatchSize, symbols.size());
        return new BatchSelection(startIndex, List.copyOf(symbols.subList(startIndex, endIndex)));
    }

    public void markSuccess(List<String> symbols, int batchSize) {
        if (symbols == null || symbols.isEmpty()) {
            cursor.set(0);
            return;
        }

        int safeBatchSize = Math.max(batchSize, 1);
        cursor.updateAndGet(current -> current + safeBatchSize >= symbols.size() ? 0 : current + safeBatchSize);
    }

    public void markFailed() {
        // Intentionally keeps the cursor unchanged so the same batch can be retried.
    }

    public boolean isCoolingDown(Clock clock) {
        Instant now = clock.instant();
        Instant blockedUntil = rateLimitedUntil.get();
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    public void markRateLimited(Duration cooldown, Clock clock) {
        Duration safeCooldown = cooldown == null || cooldown.isNegative() ? Duration.ZERO : cooldown;
        rateLimitedUntil.set(clock.instant().plus(safeCooldown));
    }

    public record BatchSelection(int startIndex, List<String> symbols) {
    }
}
