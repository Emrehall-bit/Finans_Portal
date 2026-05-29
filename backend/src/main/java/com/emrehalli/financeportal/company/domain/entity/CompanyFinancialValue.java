package com.emrehalli.financeportal.company.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "company_financial_values",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cfv_report_item",
                columnNames = {"report_id", "item_key"}
        ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyFinancialValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private CompanyFinancialReport report;

    @Column(name = "item_key", nullable = false, length = 100)
    private String itemKey;

    @Column(name = "raw_label")
    private String rawLabel;

    @Column(precision = 30, scale = 4)
    private BigDecimal value;

    @Builder.Default
    @Column(length = 10)
    private String currency = "TRY";

    @Builder.Default
    @Column(name = "unit_multiplier")
    private Integer unitMultiplier = 1;

    @Column(name = "current_period")
    private boolean currentPeriod;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}




