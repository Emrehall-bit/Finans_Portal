package com.emrehalli.financeportal.market.entity;

import com.emrehalli.financeportal.market.enums.InstrumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "market_data_snapshots",
        indexes = {
                @Index(name = "idx_market_data_snapshots_symbol_fetched_at", columnList = "symbol,fetched_at"),
                @Index(name = "idx_market_data_snapshots_instrument_type", columnList = "instrument_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDataSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 50)
    private InstrumentType instrumentType;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    @Column(name = "change_amount", precision = 19, scale = 8)
    private BigDecimal changeAmount;

    @Column(name = "change_percent", precision = 19, scale = 8)
    private BigDecimal changePercent;

    @Column(length = 20)
    private String currency;

    @Column(name = "price_time")
    private LocalDateTime priceTime;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(nullable = false, length = 100)
    private String source;
}
