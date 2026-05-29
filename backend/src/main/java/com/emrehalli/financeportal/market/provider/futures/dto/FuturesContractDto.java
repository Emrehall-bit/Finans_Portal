package com.emrehalli.financeportal.market.provider.futures.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provider-level futures contract data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuturesContractDto {

    private String contractCode;
    private String contractName;
    private BigDecimal lastPrice;
    private BigDecimal dailyChangePercent;
    private LocalDate expiryDate;
    private LocalDateTime dataTimestamp;
}




