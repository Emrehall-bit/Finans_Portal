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
    private BigDecimal previousNavValue;
    private BigDecimal getiri1a;
    private BigDecimal getiri3a;
    private BigDecimal getiri6a;
    private BigDecimal getiriYb;
    private BigDecimal getiri1y;
    private BigDecimal getiri3y;
    private BigDecimal getiri5y;
    private String riskDegeri;
    private String fonTurAciklama;
    private LocalDate navDate;
    private String fundType;
}



