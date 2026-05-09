package com.emrehalli.financeportal.market.provider.stock.dto;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provider-level stock quote data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuoteDto {

    private String symbol;
    private String companyName;
    private BigDecimal currentPrice;
    private BigDecimal changePercent;
    private LocalDateTime dataTimestamp;
    private SourceName sourceName;
}
