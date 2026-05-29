package com.emrehalli.financeportal.market.provider.bond.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provider-level bond rate data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BondRateDto {

    private String bondCode;
    private String bondName;
    private BigDecimal interestRate;
    private LocalDate maturityDate;
    private LocalDateTime dataTimestamp;
}




