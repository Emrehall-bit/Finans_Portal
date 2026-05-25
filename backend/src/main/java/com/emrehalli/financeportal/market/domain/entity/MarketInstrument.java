package com.emrehalli.financeportal.market.domain.entity;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Market instrument entity.
 */
@Entity
@Table(
        name = "market_instruments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_market_instrument_code", columnNames = "instrument_code")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_code", nullable = false, unique = true, length = 100)
    private String instrumentCode;

    @Column(name = "instrument_name", nullable = false, length = 255)
    private String instrumentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 30)
    private InstrumentType instrumentType;

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



