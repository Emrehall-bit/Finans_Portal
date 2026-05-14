package com.emrehalli.financeportal.company.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyProfileResponse {

    private String tickerCode;
    private String companyName;
    private String sector;
    private String market;
    private String kapCompanyId;
    private String mkkMemberOid;
    private boolean active;
}
