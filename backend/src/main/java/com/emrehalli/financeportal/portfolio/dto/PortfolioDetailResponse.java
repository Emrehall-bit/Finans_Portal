package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.entity.PortfolioVisibility;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PortfolioDetailResponse {
    private Long portfolioId;
    private String portfolioName;
    private PortfolioVisibility visibilityStatus;
    private LocalDateTime createdAt;
    private PortfolioSummaryResponse summary;
    private List<PortfolioHoldingDto> holdings;
}



