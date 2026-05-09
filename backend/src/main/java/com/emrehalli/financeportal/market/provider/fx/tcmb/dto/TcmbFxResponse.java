package com.emrehalli.financeportal.market.provider.fx.tcmb.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class TcmbFxResponse {

    private List<Map<String, Object>> items = new ArrayList<>();
}
