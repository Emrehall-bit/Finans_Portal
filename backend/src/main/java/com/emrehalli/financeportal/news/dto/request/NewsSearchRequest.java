package com.emrehalli.financeportal.news.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class NewsSearchRequest {

    private String scope;
    private String provider;
    private String keyword;
    private String symbol;
    private String category;
    private LocalDate fromDate;
    private LocalDate toDate;
}
