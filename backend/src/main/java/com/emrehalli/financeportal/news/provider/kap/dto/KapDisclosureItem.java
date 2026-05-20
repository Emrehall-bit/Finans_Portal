package com.emrehalli.financeportal.news.provider.kap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KapDisclosureItem {

    private KapDisclosureBasic disclosureBasic;
    private KapDisclosureDetail disclosureDetail;

    public KapDisclosureBasic getDisclosureBasic() { return disclosureBasic; }
    public void setDisclosureBasic(KapDisclosureBasic disclosureBasic) { this.disclosureBasic = disclosureBasic; }

    public KapDisclosureDetail getDisclosureDetail() { return disclosureDetail; }
    public void setDisclosureDetail(KapDisclosureDetail disclosureDetail) { this.disclosureDetail = disclosureDetail; }
}
