package com.emrehalli.financeportal.company.domain.entity;

import com.emrehalli.financeportal.company.domain.enums.DisclosureType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "company_disclosures")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDisclosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyProfile company;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_type", nullable = false, length = 20)
    private DisclosureType disclosureType;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(name = "kap_url", columnDefinition = "TEXT")
    private String kapUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "kap_year")
    private Integer kapYear;

    @Column(name = "kap_donem")
    private Integer kapDonem;

    @Column(name = "kap_period", length = 20)
    private String kapPeriod;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}




