package com.emrehalli.financeportal.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OhlcvPoint {
    private long time;
    private double value;
}
