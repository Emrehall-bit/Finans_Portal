package com.emrehalli.financeportal.portfolio.repository;

import com.emrehalli.financeportal.portfolio.entity.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {

    List<PortfolioHolding> findByPortfolioId(Long portfolioId);

    Optional<PortfolioHolding> findByIdAndPortfolioId(Long id, Long portfolioId);
}
