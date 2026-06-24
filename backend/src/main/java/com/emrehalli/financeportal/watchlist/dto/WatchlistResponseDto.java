package com.emrehalli.financeportal.watchlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Takip listesi kayıt modeli")
public class WatchlistResponseDto {

    @Schema(description = "Kayıt ID", example = "1")
    private Long id;

    @Schema(description = "Kullanıcı ID", example = "1")
    private Long userId;

    @Schema(description = "Enstrüman kodu", example = "THYAO")
    private String instrumentCode;

    @Schema(description = "Eklenme tarihi")
    private LocalDateTime createdAt;

    @Schema(description = "Güncel fiyat", example = "210.75")
    private BigDecimal currentPrice;

    @Schema(description = "Veri kaynağı", example = "YAHOO_FINANCE")
    private String source;

    @Schema(description = "Son güncelleme zamanı", example = "2026-06-24T14:30:00")
    private String lastUpdated;
}

