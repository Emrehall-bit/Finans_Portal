package com.emrehalli.financeportal.company.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "company_profiles", indexes = {
        @Index(name = "idx_company_profiles_ticker_code", columnList = "ticker_code"),
        @Index(name = "idx_company_profiles_kap_company_id", columnList = "kap_company_id"),
        @Index(name = "idx_company_profiles_mkk_member_oid", columnList = "mkk_member_oid")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker_code", unique = true, nullable = false, length = 20)
    private String tickerCode;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false)
    private String market;

    @Column(name = "kap_company_id", length = 50)
    private String kapCompanyId;

    @Column(name = "mkk_member_oid", length = 100)
    private String mkkMemberOid;

    @Column(name = "kap_disclosure_oid", length = 100)
    private String kapDisclosureOid;

    @Column(name = "shares_outstanding", precision = 30, scale = 4)
    private BigDecimal sharesOutstanding;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}



