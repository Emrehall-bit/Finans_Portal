package com.emrehalli.financeportal.company.kap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KapSgbfItem {

    private KapSgbfDisclosureBasic disclosureBasic;
}



