package com.emrehalli.financeportal.company.repository;

import com.emrehalli.financeportal.company.entity.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {

    Optional<CompanyProfile> findByTickerCodeIgnoreCase(String tickerCode);

    List<CompanyProfile> findByActiveTrue();
}
