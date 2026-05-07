package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketDataBackfillStatusEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketDataBackfillStatusRepository;
import com.emrehalli.financeportal.market.service.model.BackfillRunStatus;
import com.emrehalli.financeportal.market.service.model.MarketBackfillJobResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class MarketBackfillStatusService {

    private final MarketDataBackfillStatusRepository repository;
    private final Clock clock;

    public MarketBackfillStatusService(MarketDataBackfillStatusRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Optional<MarketDataBackfillStatusEntity> find(DataSource source, String symbol) {
        return repository.findByProviderSourceAndSymbol(source, symbol);
    }

    public boolean isEligible(DataSource source,
                              String symbol,
                              int currentHistoryCount,
                              int minimumThreshold,
                              Duration cooldown,
                              boolean force) {
        if (force) {
            return true;
        }
        if (currentHistoryCount >= minimumThreshold) {
            return false;
        }
        Optional<MarketDataBackfillStatusEntity> existing = find(source, symbol);
        if (existing.isEmpty()) {
            return true;
        }
        MarketDataBackfillStatusEntity status = existing.get();
        if (BackfillRunStatus.SUCCESS.name().equals(status.getStatus())) {
            return false;
        }
        if (status.getLastAttemptAt() == null) {
            return true;
        }
        return status.getLastAttemptAt().plus(cooldown).isBefore(clock.instant());
    }

    public void markRunning(DataSource source, String symbol) {
        markRunning(source, symbol, 0);
    }

    public void markRunning(DataSource source, String symbol, int totalChunks) {
        MarketDataBackfillStatusEntity entity = repository.findByProviderSourceAndSymbol(source, symbol)
                .orElseGet(MarketDataBackfillStatusEntity::new);
        entity.setProviderSource(source);
        entity.setSymbol(symbol);
        entity.setStatus(BackfillRunStatus.RUNNING.name());
        entity.setLastAttemptAt(clock.instant());
        entity.setTotalChunks(Math.max(totalChunks, 0));
        entity.setCompletedChunks(0);
        entity.setLastProcessedDate(null);
        entity.setLastErrorMessage(null);
        repository.save(entity);
    }

    public void updateProgress(DataSource source,
                               String symbol,
                               int totalChunks,
                               int completedChunks,
                               LocalDate lastProcessedDate) {
        MarketDataBackfillStatusEntity entity = repository.findByProviderSourceAndSymbol(source, symbol)
                .orElseGet(MarketDataBackfillStatusEntity::new);
        entity.setProviderSource(source);
        entity.setSymbol(symbol);
        entity.setStatus(BackfillRunStatus.RUNNING.name());
        entity.setTotalChunks(Math.max(totalChunks, 0));
        entity.setCompletedChunks(Math.max(completedChunks, 0));
        entity.setLastProcessedDate(lastProcessedDate);
        repository.save(entity);
    }

    public MarketBackfillJobResult markCompleted(DataSource source,
                                                 String symbol,
                                                 BackfillRunStatus status,
                                                 int fetchedCount,
                                                 int savedCount,
                                                 LocalDate minDate,
                                                 LocalDate maxDate,
                                                 String message) {
        MarketDataBackfillStatusEntity entity = repository.findByProviderSourceAndSymbol(source, symbol)
                .orElseGet(MarketDataBackfillStatusEntity::new);
        entity.setProviderSource(source);
        entity.setSymbol(symbol);
        entity.setStatus(status.name());
        entity.setLastAttemptAt(clock.instant());
        entity.setFetchedCount(fetchedCount);
        entity.setSavedCount(savedCount);
        entity.setMinDate(minDate);
        entity.setMaxDate(maxDate);
        entity.setLastErrorMessage(message);
        if (entity.getTotalChunks() == null) {
            entity.setTotalChunks(0);
        }
        if (entity.getCompletedChunks() == null) {
            entity.setCompletedChunks(0);
        }
        if (status == BackfillRunStatus.SUCCESS) {
            entity.setLastSuccessAt(clock.instant());
            entity.setRetryCount(0);
        } else if (status == BackfillRunStatus.FAILED) {
            entity.setRetryCount((entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1);
        }
        repository.save(entity);
        return new MarketBackfillJobResult(source, symbol, status, fetchedCount, savedCount, minDate, maxDate,
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(), message);
    }
}
