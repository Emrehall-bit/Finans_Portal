package com.emrehalli.financeportal.news.provider.kap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class KapDisclosureListRequest {

    private String fromDate;
    private String toDate;
    private List<String> disclosureTypes;
    private List<String> memberTypes;
    private Object mkkMemberOid;

    public KapDisclosureListRequest(String fromDate, String toDate, List<String> disclosureTypes, List<String> memberTypes) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.disclosureTypes = disclosureTypes;
        this.memberTypes = memberTypes;
        this.mkkMemberOid = null;
    }

    public String getFromDate() { return fromDate; }
    public String getToDate() { return toDate; }
    public List<String> getDisclosureTypes() { return disclosureTypes; }
    public List<String> getMemberTypes() { return memberTypes; }
    public Object getMkkMemberOid() { return mkkMemberOid; }
}
