package com.emrehalli.financeportal.company.kap.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DisclosureFailedItemDto {

    private String title;
    private String disclosureIndex;
    private String reason;
}



