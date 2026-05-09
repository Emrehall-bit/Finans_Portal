package com.emrehalli.financeportal.market.domain.entity;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Market price entity.
 */
@Entity
@Table(
        name = "market_prices",
        indexes = {
                @Index(name = "idx_market_price_instrument_id", columnList = "instrument_id"),
                @Index(name = "idx_market_price_timestamp", columnList = "price_timestamp")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "price_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal priceValue;

    @Column(name = "price_timestamp", nullable = false)
    private LocalDateTime priceTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_name", nullable = false, length = 50)
    private SourceName sourceName;
}
