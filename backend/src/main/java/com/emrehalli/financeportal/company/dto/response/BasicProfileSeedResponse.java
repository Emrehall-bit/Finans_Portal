package com.emrehalli.financeportal.company.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BasicProfileSeedResponse {

    private int created;
    private int skippedExisting;
    private List<BasicProfileSeedError> errors;
}

