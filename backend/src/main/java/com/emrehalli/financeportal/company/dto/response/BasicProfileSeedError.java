package com.emrehalli.financeportal.company.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BasicProfileSeedError {

    private String ticker;
    private String message;
}
