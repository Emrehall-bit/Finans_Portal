package com.emrehalli.financeportal.portfolio.repository;

import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUserId(Long userId);

    boolean existsByIdAndUserKeycloakId(Long id, String keycloakId);
}
