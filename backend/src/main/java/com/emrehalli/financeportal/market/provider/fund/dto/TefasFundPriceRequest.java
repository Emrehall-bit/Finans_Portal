package com.emrehalli.financeportal.market.provider.fund.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TefasFundPriceRequest {

    private String fonKodu;
    private String dil = "TR";
    private int periyod;
}




