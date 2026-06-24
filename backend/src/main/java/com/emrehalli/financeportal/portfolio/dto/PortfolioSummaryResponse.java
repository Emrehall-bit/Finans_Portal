package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.enums.SummaryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Portföy finansal özet modeli")
public class PortfolioSummaryResponse {

    @Schema(description = "Toplam maliyet", example = "50000.00")
    private BigDecimal totalCost;

    @Schema(description = "Güncel toplam değer", example = "55250.00")
    private BigDecimal currentValue;

    @Schema(description = "Günlük kâr/zarar", example = "320.50")
    private BigDecimal dailyProfitLoss;

    @Schema(description = "Günlük kâr/zarar yüzdesi", example = "0.58")
    private BigDecimal dailyProfitLossPercent;

    @Schema(description = "Toplam kâr/zarar", example = "5250.00")
    private BigDecimal profitLoss;

    @Schema(description = "Toplam kâr/zarar yüzdesi", example = "10.50")
    private BigDecimal profitLossPercent;

    @Schema(description = "Portföy özet durumu (PROFIT, LOSS, NEUTRAL)", example = "PROFIT")
    private SummaryStatus summaryStatus;

    @Schema(description = "Fiyatı alınamayan varlık sayısı", example = "0")
    private int missingPriceCount;

    @Schema(description = "Toplam güncel değer (geriye uyumluluk)")
    private BigDecimal totalCurrentValue;

    @Schema(description = "Toplam kâr/zarar (geriye uyumluluk)")
    private BigDecimal totalProfitLoss;

    public PortfolioSummaryResponse() {
    }

    public PortfolioSummaryResponse(BigDecimal totalCost,
                                    BigDecimal currentValue,
                                    BigDecimal dailyProfitLoss,
                                    BigDecimal dailyProfitLossPercent,
                                    BigDecimal profitLoss,
                                    BigDecimal profitLossPercent,
                                    SummaryStatus summaryStatus,
                                    int missingPriceCount) {
        this.totalCost = totalCost;
        this.currentValue = currentValue;
        this.dailyProfitLoss = dailyProfitLoss;
        this.dailyProfitLossPercent = dailyProfitLossPercent;
        this.profitLoss = profitLoss;
        this.profitLossPercent = profitLossPercent;
        this.summaryStatus = summaryStatus;
        this.missingPriceCount = missingPriceCount;

        this.totalCurrentValue = currentValue;
        this.totalProfitLoss = profitLoss;
    }
}

