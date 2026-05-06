package com.emrehalli.financeportal.market.persistence.entity;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "market_provider_mappings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_market_provider_mappings_source_symbol", columnNames = {"provider_source", "provider_symbol"})
        },
        indexes = {
                @Index(name = "idx_market_provider_mappings_source_enabled_priority", columnList = "provider_source,enabled,priority"),
                @Index(name = "idx_market_provider_mappings_instrument_id", columnList = "instrument_id")
        }
)
public class MarketProviderMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, foreignKey = @ForeignKey(name = "fk_market_provider_mappings_instrument"))
    private MarketInstrumentEntity instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_source", nullable = false, length = 50)
    private DataSource providerSource;

    @Column(name = "provider_symbol", nullable = false, length = 100)
    private String providerSymbol;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "refresh_interval_seconds")
    private Integer refreshIntervalSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public MarketInstrumentEntity getInstrument() {
        return instrument;
    }

    public void setInstrument(MarketInstrumentEntity instrument) {
        this.instrument = instrument;
    }

    public DataSource getProviderSource() {
        return providerSource;
    }

    public void setProviderSource(DataSource providerSource) {
        this.providerSource = providerSource;
    }

    public String getProviderSymbol() {
        return providerSymbol;
    }

    public void setProviderSymbol(String providerSymbol) {
        this.providerSymbol = providerSymbol;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Integer getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(Integer refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
