package com.emrehalli.financeportal.company.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinancialBackfillRequest {
    private Integer startYear;
    private Integer endYear;
}
