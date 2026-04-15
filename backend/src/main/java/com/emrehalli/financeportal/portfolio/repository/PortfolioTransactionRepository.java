package com.emrehalli.financeportal.portfolio.repository;

import com.emrehalli.financeportal.portfolio.entity.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {
    List<PortfolioTransaction> findByPortfolioIdOrderByTransactionTimeDesc(Long portfolioId);
    List<PortfolioTransaction> findByPortfolioId(Long portfolioId);
}