package com.emrehalli.financeportal.company.repository;

import com.emrehalli.financeportal.company.entity.CompanyDisclosure;
import com.emrehalli.financeportal.company.enums.DisclosureType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface CompanyDisclosureRepository extends JpaRepository<CompanyDisclosure, Long> {

    Page<CompanyDisclosure> findByCompanyTickerCodeIgnoreCaseOrderByPublishedAtDesc(String tickerCode, Pageable pageable);

    List<CompanyDisclosure> findByCompanyTickerCodeIgnoreCaseAndDisclosureType(String tickerCode, DisclosureType disclosureType);

    boolean existsByKapUrl(String kapUrl);

    boolean existsByCompanyIdAndTitleAndPublishedAt(Long companyId, String title, OffsetDateTime publishedAt);
}
