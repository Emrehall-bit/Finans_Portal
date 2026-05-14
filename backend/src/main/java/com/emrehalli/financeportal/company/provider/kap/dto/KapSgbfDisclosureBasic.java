package com.emrehalli.financeportal.company.provider.kap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KapSgbfDisclosureBasic {

    private String title;
    private String companyTitle;
    private String stockCode;
    private String publishDate;
    private String disclosureId;
    private String disclosureIndex;
    private String summary;
    private String disclosureType;
    private String disclosureCategory;
    private String year;
    private String donem;
    private String period;
}
