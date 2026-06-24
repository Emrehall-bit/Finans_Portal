package com.emrehalli.financeportal.market.provider.fund.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TefasFundPriceResponseItem {

    private String fonKodu;
    private String fonUnvan;
    private String tarih;
    private BigDecimal fiyat;
}

