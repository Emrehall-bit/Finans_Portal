package com.emrehalli.financeportal.company.dto.response;

import com.emrehalli.financeportal.company.domain.enums.DisclosureType;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class CompanyDisclosureResponse {

    private Long id;
    private DisclosureType disclosureType;
    private String title;
    private String kapUrl;
    private OffsetDateTime publishedAt;
    private String summary;
    private OffsetDateTime createdAt;
}




