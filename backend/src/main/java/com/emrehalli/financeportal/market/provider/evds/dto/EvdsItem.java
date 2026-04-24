package com.emrehalli.financeportal.market.provider.evds.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class EvdsItem {

    private String date;
    private final Map<String, String> values = new HashMap<>();

    @JsonAnySetter
    public void put(String key, Object value) {
        if ("Tarih".equalsIgnoreCase(key) || "date".equalsIgnoreCase(key)) {
            this.date = value == null ? null : value.toString();
            return;
        }

        values.put(key, value == null ? null : value.toString());
    }

    public String date() {
        return date;
    }

    public Map<String, String> values() {
        return values;
    }
}
