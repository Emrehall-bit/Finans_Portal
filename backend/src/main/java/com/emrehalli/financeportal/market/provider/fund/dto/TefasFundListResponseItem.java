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
public class TefasFundListResponseItem {

    private String fonKodu;
    private String fonUnvan;
    private String fonTurAciklama;
    private Boolean tefasDurum;
    private BigDecimal getiri1a;
    private BigDecimal getiri3a;
    private BigDecimal getiri6a;
    private BigDecimal getiri1y;
    private BigDecimal getiri3y;
    private BigDecimal getiri5y;
    private BigDecimal getiriYb;
    private String riskDegeri;
}

