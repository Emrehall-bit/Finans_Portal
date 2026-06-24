package com.emrehalli.financeportal.market.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@Schema(description = "OHLCV fiyat geçmişi veri noktası")
public class PriceHistoryDto {

    @Schema(description = "Zaman damgası")
    private Instant priceTimestamp;

    @Schema(description = "Açılış fiyatı", example = "185.00")
    private BigDecimal openPrice;

    @Schema(description = "En yüksek fiyat", example = "192.50")
    private BigDecimal highPrice;

    @Schema(description = "En düşük fiyat", example = "183.20")
    private BigDecimal lowPrice;

    @Schema(description = "Kapanış fiyatı", example = "190.75")
    private BigDecimal closePrice;

    @Schema(description = "İşlem hacmi", example = "12500000")
    private BigDecimal volume;
}

