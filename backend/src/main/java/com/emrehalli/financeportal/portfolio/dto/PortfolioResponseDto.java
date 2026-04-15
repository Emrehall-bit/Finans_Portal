package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.entity.PortfolioVisibility;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PortfolioResponseDto {
    private Long portfolioId;
    private String portfolioName;
    private PortfolioVisibility visibilityStatus;
    private LocalDateTime createdAt;
    private Long userId;
}
