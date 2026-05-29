package com.emrehalli.financeportal.technicalanalysis.fundamental.entity;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "fundamental_ratios",
    uniqueConstraints = @UniqueConstraint(columnNames = {"instrument_id", "period"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundamentalRatios {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "period", nullable = false, length = 10)
    private String period;

    @Column(name = "calculation_price", nullable = false, precision = 10, scale = 4)
    private BigDecimal calculationPrice;

    @Column(name = "pe_ratio", precision = 10, scale = 4)
    private BigDecimal peRatio;

    @Column(name = "pb_ratio", precision = 10, scale = 4)
    private BigDecimal pbRatio;

    @Column(name = "graham_number", precision = 10, scale = 4)
    private BigDecimal grahamNumber;

    @Column(name = "gross_margin", precision = 8, scale = 4)
    private BigDecimal grossMargin;

    @Column(name = "net_margin", precision = 8, scale = 4)
    private BigDecimal netMargin;

    @Column(name = "roe", precision = 8, scale = 4)
    private BigDecimal roe;

    @Column(name = "roa", precision = 8, scale = 4)
    private BigDecimal roa;

    @Column(name = "revenue_growth_yoy", precision = 8, scale = 4)
    private BigDecimal revenueGrowthYoy;

    @Column(name = "net_income_growth_yoy", precision = 8, scale = 4)
    private BigDecimal netIncomeGrowthYoy;

    @Column(name = "asset_growth_yoy", precision = 8, scale = 4)
    private BigDecimal assetGrowthYoy;

    @Column(name = "debt_to_equity", precision = 8, scale = 4)
    private BigDecimal debtToEquity;

    @Column(name = "current_ratio", precision = 8, scale = 4)
    private BigDecimal currentRatio;

    @Column(name = "piotroski_score")
    private Integer piotroskiScore;

    @Column(name = "altman_z_score", precision = 8, scale = 4)
    private BigDecimal altmanZScore;

    @Column(name = "overall_signal", length = 10)
    private String overallSignal;

    @Column(name = "calculated_at")
    private OffsetDateTime calculatedAt;

    @PrePersist
    @PreUpdate
    public void preWrite() {
        calculatedAt = OffsetDateTime.now();
    }
}

