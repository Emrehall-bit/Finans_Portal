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

    // Cursor tracks which slice of the configured BIST symbol list should be served next
    // when the provider is operating in non-explicit round-robin mode.
    private final AtomicInteger cursor = new AtomicInteger(0);

    // Rate-limit state is shared across requests so Yahoo cooldown survives between scheduler ticks.
    private final AtomicReference<Instant> rateLimitedUntil = new AtomicReference<>(Instant.EPOCH);

    public BatchSelection nextBatch(List<String> symbols, int batchSize) {
        if (symbols == null || symbols.isEmpty()) {
            return new BatchSelection(0, List.of());
        }

        // Batch size is clamped to at least one symbol to avoid a stalled cursor.
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

        // On success the cursor advances by the batch size; wrapping to zero restarts the cycle.
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
        // Cooldown expiration is calculated once so later calls can cheaply check blocking state.
        Duration safeCooldown = cooldown == null || cooldown.isNegative() ? Duration.ZERO : cooldown;
        rateLimitedUntil.set(clock.instant().plus(safeCooldown));
    }

    public record BatchSelection(int startIndex, List<String> symbols) {
    }
}
