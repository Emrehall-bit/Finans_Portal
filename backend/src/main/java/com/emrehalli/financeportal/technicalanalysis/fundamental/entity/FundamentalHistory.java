package com.emrehalli.financeportal.technicalanalysis.fundamental.entity;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fundamental_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundamentalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "period", nullable = false, length = 10)
    private String period;

    @Column(name = "revenue", precision = 20, scale = 2)
    private BigDecimal revenue;

    @Column(name = "net_income", precision = 20, scale = 2)
    private BigDecimal netIncome;

    @Column(name = "gross_margin", precision = 8, scale = 4)
    private BigDecimal grossMargin;

    @Column(name = "net_margin", precision = 8, scale = 4)
    private BigDecimal netMargin;

    @Column(name = "roe", precision = 8, scale = 4)
    private BigDecimal roe;

    @Column(name = "pe_ratio", precision = 10, scale = 4)
    private BigDecimal peRatio;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

