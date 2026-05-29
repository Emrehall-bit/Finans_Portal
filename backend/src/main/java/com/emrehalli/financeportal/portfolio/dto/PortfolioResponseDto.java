package com.emrehalli.financeportal.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PortfolioResponseDto {
    private Long portfolioId;
    private String portfolioName;
    private LocalDateTime createdAt;
    private Long userId;
}







