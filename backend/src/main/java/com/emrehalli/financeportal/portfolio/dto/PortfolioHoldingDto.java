package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "Portföy varlık (holding) bilgi modeli")
public class PortfolioHoldingDto {

    @Schema(description = "Varlık ID", example = "1")
    private Long holdingId;

    @Schema(description = "Enstrüman kodu", example = "THYAO")
    private String instrumentCode;

    @Schema(description = "Miktar", example = "100")
    private BigDecimal quantity;

    @Schema(description = "Alış fiyatı", example = "185.50")
    private BigDecimal buyPrice;

    @Schema(description = "Güncel fiyat", example = "210.75")
    private BigDecimal currentPrice;

    @Schema(description = "Güncel değer (miktar × güncel fiyat)", example = "21075.00")
    private BigDecimal currentValue;

    @Schema(description = "Günlük kâr/zarar", example = "150.00")
    private BigDecimal dailyProfitLoss;

    @Schema(description = "Günlük değişim yüzdesi", example = "0.72")
    private BigDecimal dailyChangePercent;

    @Schema(description = "Toplam kâr/zarar", example = "2525.00")
    private BigDecimal profitLoss;

    @Schema(description = "Toplam kâr/zarar yüzdesi", example = "13.61")
    private BigDecimal profitLossPercent;

    @Schema(description = "Fiyat verisi durumu", example = "LIVE")
    private PriceStatus priceStatus;

    @Schema(description = "Son fiyat güncelleme zamanı")
    private LocalDateTime lastPriceUpdateTime;

    @Schema(description = "Değerleme mevcut mu", example = "true")
    private boolean valuationAvailable;

    @Schema(description = "Alış tarihi", example = "2026-01-15")
    private LocalDate purchaseDate;

    @Schema(description = "Oluşturulma tarihi")
    private LocalDateTime createdAt;

    @Schema(description = "Güncellenme tarihi")
    private LocalDateTime updatedAt;

    @Schema(description = "Birleştirilmiş giriş sayısı", example = "1")
    private int entryCount;

    @Schema(description = "Kaynak holding ID'leri")
    private List<Long> sourceHoldingIds;
}

