package com.emrehalli.financeportal.analysis.entity;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "company_financials",
    uniqueConstraints = @UniqueConstraint(columnNames = {"instrument_id", "period", "period_type"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyFinancials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "period", nullable = false, length = 10)
    private String period;

    @Column(name = "period_type", nullable = false, length = 10)
    private String periodType;

    @Column(name = "revenue", precision = 20, scale = 2)
    private BigDecimal revenue;

    @Column(name = "gross_profit", precision = 20, scale = 2)
    private BigDecimal grossProfit;

    @Column(name = "net_income", precision = 20, scale = 2)
    private BigDecimal netIncome;

    @Column(name = "total_assets", precision = 20, scale = 2)
    private BigDecimal totalAssets;

    @Column(name = "total_equity", precision = 20, scale = 2)
    private BigDecimal totalEquity;

    @Column(name = "total_liabilities", precision = 20, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(name = "current_assets", precision = 20, scale = 2)
    private BigDecimal currentAssets;

    @Column(name = "current_liabilities", precision = 20, scale = 2)
    private BigDecimal currentLiabilities;

    @Column(name = "operating_cash_flow", precision = 20, scale = 2)
    private BigDecimal operatingCashFlow;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "data_fetched_at")
    private OffsetDateTime dataFetchedAt;

    @PrePersist
    public void prePersist() {
        if (dataFetchedAt == null) {
            dataFetchedAt = OffsetDateTime.now();
        }
    }
}
