package com.emrehalli.financeportal.company.domain.entity;

import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.domain.enums.ReportType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "company_financial_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cfr_company_period_type",
                columnNames = {"company_id", "period_year", "period_quarter", "report_type"}
        ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyFinancialReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyProfile company;

    @Column(name = "period_year")
    private Integer periodYear;

    @Column(name = "period_quarter")
    private Integer periodQuarter;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 20)
    private ReportType reportType;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", length = 20)
    private ParseStatus parseStatus;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}



