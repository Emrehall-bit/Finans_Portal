package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.enums.SummaryStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PortfolioSummaryResponse {

    private BigDecimal totalCost;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    private SummaryStatus summaryStatus;
    private int missingPriceCount;

    // backward compatibility
    private BigDecimal totalCurrentValue;
    private BigDecimal totalProfitLoss;

    public PortfolioSummaryResponse() {
    }

    public PortfolioSummaryResponse(BigDecimal totalCost,
                                    BigDecimal currentValue,
                                    BigDecimal profitLoss,
                                    BigDecimal profitLossPercent,
                                    SummaryStatus summaryStatus,
                                    int missingPriceCount) {
        this.totalCost = totalCost;
        this.currentValue = currentValue;
        this.profitLoss = profitLoss;
        this.profitLossPercent = profitLossPercent;
        this.summaryStatus = summaryStatus;
        this.missingPriceCount = missingPriceCount;

        this.totalCurrentValue = currentValue;
        this.totalProfitLoss = profitLoss;
    }
}



