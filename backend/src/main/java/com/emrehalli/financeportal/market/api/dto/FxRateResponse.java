package com.emrehalli.financeportal.market.api.dto;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FX rate response payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateResponse {

    private String code;
    private String name;
    private String source;
    private String type;
    private BigDecimal buyRate;
    private BigDecimal sellRate;
    private BigDecimal last;
    private BigDecimal changePercent;
    private LocalDateTime priceTimestamp;
}




