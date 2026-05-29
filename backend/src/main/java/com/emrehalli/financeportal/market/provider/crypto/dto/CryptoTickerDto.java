package com.emrehalli.financeportal.market.provider.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provider-level crypto ticker data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoTickerDto {

    private String symbol;
    private BigDecimal price;
    private BigDecimal dailyChangePercent;
    private LocalDateTime dataTimestamp;
}




