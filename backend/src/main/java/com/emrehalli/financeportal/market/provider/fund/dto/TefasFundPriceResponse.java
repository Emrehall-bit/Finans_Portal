package com.emrehalli.financeportal.market.provider.fund.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TefasFundPriceResponse {

    private String errorCode;
    private String errorMessage;
    private List<TefasFundPriceResponseItem> resultList;
}

