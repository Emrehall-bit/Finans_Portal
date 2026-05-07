package com.emrehalli.financeportal.market.persistence.entity;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stores provider-specific lookup and scheduling metadata for an instrument.
 */
@Entity
@Table(name = "market_provider_mappings")
@Getter
@Setter
public class MarketProviderMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrumentEntity instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private DataSource source;

    @Column(name = "external_symbol", nullable = false, length = 100)
    private String externalSymbol;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "refresh_interval_minutes", nullable = false)
    private int refreshIntervalMinutes;

    @Column(name = "history_start_date")
    private LocalDate historyStartDate;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_refresh_status", nullable = false, length = 20)
    private MappingRefreshStatus lastRefreshStatus = MappingRefreshStatus.PENDING;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "last_failure_reason", length = 2000)
    private String lastFailureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Initializes generated and audit fields before insert.
     */
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
        lastRefreshStatus = lastRefreshStatus == null ? MappingRefreshStatus.PENDING : lastRefreshStatus;
    }

    /**
     * Updates the modification timestamp before update.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
