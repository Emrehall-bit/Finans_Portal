package com.emrehalli.financeportal.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_provider_sync_state")
public class NewsProviderSyncState {

    @Id
    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "last_successful_sync_at")
    private LocalDateTime lastSuccessfulSyncAt;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalDateTime getLastSuccessfulSyncAt() {
        return lastSuccessfulSyncAt;
    }

    public void setLastSuccessfulSyncAt(LocalDateTime lastSuccessfulSyncAt) {
        this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
    }
}

