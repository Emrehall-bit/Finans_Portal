package com.emrehalli.financeportal.market.domain.entity;

import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Market price history entity.
 */
@Entity
@Table(
        name = "market_price_history",
        indexes = {
                @Index(name = "idx_price_history_instrument_time", columnList = "instrument_id, price_timestamp")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "open_price", precision = 19, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 19, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 19, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", precision = 19, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "volume", precision = 30, scale = 8)
    private BigDecimal volume;

    @Column(name = "price_timestamp", nullable = false)
    private LocalDateTime priceTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_type", nullable = false, length = 10)
    private IntervalType intervalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_name", nullable = false, length = 50)
    private SourceName sourceName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
