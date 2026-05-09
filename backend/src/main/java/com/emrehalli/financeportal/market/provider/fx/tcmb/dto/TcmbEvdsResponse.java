package com.emrehalli.financeportal.market.provider.fx.tcmb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TcmbEvdsResponse {

    private List<Map<String, Object>> items = new ArrayList<>();
}
