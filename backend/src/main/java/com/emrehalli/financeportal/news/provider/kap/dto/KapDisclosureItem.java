package com.emrehalli.financeportal.news.provider.kap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KapDisclosureItem {

    private KapDisclosureBasic disclosureBasic;
    private String year;
    private String period;

    public KapDisclosureBasic getDisclosureBasic() { return disclosureBasic; }
    public void setDisclosureBasic(KapDisclosureBasic disclosureBasic) { this.disclosureBasic = disclosureBasic; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
}
