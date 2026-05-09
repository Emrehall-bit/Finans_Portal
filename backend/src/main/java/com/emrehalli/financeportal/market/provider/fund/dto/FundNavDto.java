package com.emrehalli.financeportal.market.provider.fund.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Provider-level fund NAV data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundNavDto {

    private String fundCode;
    private String fundName;
    private BigDecimal navValue;
    private LocalDate navDate;
    private String fundType;
}
