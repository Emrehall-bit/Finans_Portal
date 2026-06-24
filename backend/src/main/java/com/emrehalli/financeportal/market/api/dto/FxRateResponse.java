package com.emrehalli.financeportal.market.api.dto;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Döviz kuru yanıt modeli")
public class FxRateResponse {

    @Schema(description = "Döviz kodu", example = "USD")
    private String code;

    @Schema(description = "Döviz adı", example = "Amerikan Doları")
    private String name;

    @Schema(description = "Veri kaynağı", example = "TCMB")
    private String source;

    @Schema(description = "Kur tipi", example = "FX")
    private String type;

    @Schema(description = "Alış kuru", example = "38.45")
    private BigDecimal buyRate;

    @Schema(description = "Satış kuru", example = "38.52")
    private BigDecimal sellRate;

    @Schema(description = "Son fiyat", example = "38.48")
    private BigDecimal last;

    @Schema(description = "Günlük değişim yüzdesi", example = "0.35")
    private BigDecimal changePercent;

    @Schema(description = "Fiyat zaman damgası")
    private LocalDateTime priceTimestamp;
}

