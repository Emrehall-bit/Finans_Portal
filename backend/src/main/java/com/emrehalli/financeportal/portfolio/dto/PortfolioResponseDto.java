package com.emrehalli.financeportal.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "Portföy temel bilgi yanıt modeli")
public class PortfolioResponseDto {

    @Schema(description = "Portföy ID", example = "1")
    private final Long portfolioId;

    @Schema(description = "Portföy adı", example = "Uzun Vadeli Yatırımlar")
    private final String portfolioName;

    @Schema(description = "Oluşturulma tarihi")
    private final LocalDateTime createdAt;

    @Schema(description = "Sahip kullanıcı ID", example = "1")
    private final Long userId;

    public PortfolioResponseDto(Long portfolioId, String portfolioName, LocalDateTime createdAt, Long userId) {
        this.portfolioId = portfolioId;
        this.portfolioName = portfolioName;
        this.createdAt = createdAt;
        this.userId = userId;
    }
}

