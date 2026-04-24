package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PortfolioHoldingDto {

    private Long holdingId;
    private String instrumentCode;
    private BigDecimal quantity;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    private PriceStatus priceStatus;
    private LocalDateTime lastPriceUpdateTime;
    private boolean valuationAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}



