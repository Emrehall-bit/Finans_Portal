package com.emrehalli.financeportal.market.persistence.entity;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_data_backfill_status",
        uniqueConstraints = @UniqueConstraint(name = "uk_market_backfill_status_provider_symbol", columnNames = {"provider_source", "symbol"}))
public class MarketDataBackfillStatusEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_source", nullable = false, length = 50)
    private DataSource providerSource;

    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error_message", length = 2000)
    private String lastErrorMessage;

    @Column(name = "fetched_count")
    private Integer fetchedCount;

    @Column(name = "saved_count")
    private Integer savedCount;

    @Column(name = "min_date")
    private LocalDate minDate;

    @Column(name = "max_date")
    private LocalDate maxDate;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public DataSource getProviderSource() { return providerSource; }
    public void setProviderSource(DataSource providerSource) { this.providerSource = providerSource; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public Integer getFetchedCount() { return fetchedCount; }
    public void setFetchedCount(Integer fetchedCount) { this.fetchedCount = fetchedCount; }
    public Integer getSavedCount() { return savedCount; }
    public void setSavedCount(Integer savedCount) { this.savedCount = savedCount; }
    public LocalDate getMinDate() { return minDate; }
    public void setMinDate(LocalDate minDate) { this.minDate = minDate; }
    public LocalDate getMaxDate() { return maxDate; }
    public void setMaxDate(LocalDate maxDate) { this.maxDate = maxDate; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
}
