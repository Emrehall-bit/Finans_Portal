package com.emrehalli.financeportal.market.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Piyasa fiyat teklifi yanıt modeli")
public record MarketQuoteResponse(
        @Schema(description = "Enstrüman sembolü", example = "XU100") String symbol,
        @Schema(description = "Enstrüman adı", example = "BIST 100") String name,
        @Schema(description = "Son fiyat", example = "10250.45") BigDecimal price,
        @Schema(description = "Değişim oranı (%)", example = "1.25") BigDecimal changeRate,
        @Schema(description = "Veri kaynağı", example = "YAHOO_FINANCE") String source,
        @Schema(description = "Güncelleme zamanı") LocalDateTime updatedAt,
        @Schema(description = "Enstrüman türü", example = "INDEX") String instrumentType,
        @Schema(description = "Görüntüleme birimi", example = "ons") String displayUnit,
        @Schema(description = "Para birimi", example = "TRY") String currency
) {
}

