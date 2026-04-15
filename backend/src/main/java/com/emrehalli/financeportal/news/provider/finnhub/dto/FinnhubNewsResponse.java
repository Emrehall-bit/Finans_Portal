package com.emrehalli.financeportal.news.provider.finnhub.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinnhubNewsResponse {

    private String category;
    private long datetime;
    private String headline;
    private long id;
    private String related;
    private String source;
    private String summary;
    private String url;
}
