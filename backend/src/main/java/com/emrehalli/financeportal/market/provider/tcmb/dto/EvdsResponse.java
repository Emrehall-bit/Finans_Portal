package com.emrehalli.financeportal.market.provider.tcmb.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class EvdsResponse {

    private Integer totalCount;
    private List<Map<String, Object>> items;
}
