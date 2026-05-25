package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PortfolioHoldingDto {

    private Long holdingId;
    private String instrumentCode;
    private BigDecimal quantity;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal dailyProfitLoss;
    private BigDecimal dailyChangePercent;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    private PriceStatus priceStatus;
    private LocalDateTime lastPriceUpdateTime;
    private boolean valuationAvailable;
    private LocalDate purchaseDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int entryCount;
    private List<Long> sourceHoldingIds;
}





