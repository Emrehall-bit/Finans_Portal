package com.emrehalli.financeportal.company.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "company_ratios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyRatio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyProfile company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private CompanyFinancialReport report;

    @Column(name = "price_at_calc", precision = 20, scale = 6)
    private BigDecimal priceAtCalc;

    @Column(name = "market_cap", precision = 30, scale = 2)
    private BigDecimal marketCap;

    @Column(name = "pe_ratio", precision = 15, scale = 4)
    private BigDecimal peRatio;

    @Column(name = "pb_ratio", precision = 15, scale = 4)
    private BigDecimal pbRatio;

    @Column(name = "debt_to_equity", precision = 15, scale = 4)
    private BigDecimal debtToEquity;

    @Column(name = "gross_margin", precision = 10, scale = 6)
    private BigDecimal grossMargin;

    @Column(name = "net_margin", precision = 10, scale = 6)
    private BigDecimal netMargin;

    @Column(precision = 10, scale = 6)
    private BigDecimal roe;

    @Column(precision = 10, scale = 6)
    private BigDecimal roa;

    @Column(name = "revenue_growth", precision = 10, scale = 6)
    private BigDecimal revenueGrowth;

    @Column(name = "revenue_growth_label", length = 80)
    private String revenueGrowthLabel;

    @Column(name = "net_profit_growth", precision = 10, scale = 6)
    private BigDecimal netProfitGrowth;

    @Column(name = "net_profit_growth_label", length = 80)
    private String netProfitGrowthLabel;

    @Column(name = "asset_growth", precision = 10, scale = 6)
    private BigDecimal assetGrowth;

    @Column(name = "asset_growth_label", length = 80)
    private String assetGrowthLabel;

    @Column(name = "health_label", length = 50)
    private String healthLabel;

    @Column(name = "calculated_at")
    private OffsetDateTime calculatedAt;
}
