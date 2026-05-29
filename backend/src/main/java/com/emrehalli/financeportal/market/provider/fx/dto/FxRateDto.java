package com.emrehalli.financeportal.market.provider.fx.dto;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provider-level FX rate data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateDto {

    private SourceName sourceName;
    private String currencyCode;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private BigDecimal referencePrice;
    private LocalDateTime dataTimestamp;
}




