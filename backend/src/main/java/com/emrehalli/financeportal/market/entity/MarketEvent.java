package com.emrehalli.financeportal.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "market_events",
        indexes = {
                @Index(name = "idx_market_events_symbol_published_at", columnList = "symbol,published_at"),
                @Index(name = "idx_market_events_source_event_type", columnList = "source,event_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 50)
    private String symbol;

    @Column(name = "issuer_code", length = 50)
    private String issuerCode;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(length = 1200)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
