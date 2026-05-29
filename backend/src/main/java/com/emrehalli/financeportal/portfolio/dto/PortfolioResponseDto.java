package com.emrehalli.financeportal.portfolio.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PortfolioResponseDto {
    private final Long portfolioId;
    private final String portfolioName;
    private final LocalDateTime createdAt;
    private final Long userId;

    public PortfolioResponseDto(Long portfolioId, String portfolioName, LocalDateTime createdAt, Long userId) {
        this.portfolioId = portfolioId;
        this.portfolioName = portfolioName;
        this.createdAt = createdAt;
        this.userId = userId;
    }
}







