package com.emrehalli.financeportal.company.kap.dto;

import com.emrehalli.financeportal.company.domain.enums.DisclosureType;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class KapDisclosureDto {

    private String title;
    private String disclosureIndex;
    private String kapUrl;
    private OffsetDateTime publishedAt;
    private DisclosureType disclosureType;
    private String summary;
    private Integer kapYear;
    private Integer kapDonem;
    private String kapPeriod;
}



