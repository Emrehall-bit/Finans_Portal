package com.emrehalli.financeportal.news.provider.kap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KapDisclosureDetail {

    private Boolean oldKap;

    public Boolean getOldKap() { return oldKap; }
    public void setOldKap(Boolean oldKap) { this.oldKap = oldKap; }
}



