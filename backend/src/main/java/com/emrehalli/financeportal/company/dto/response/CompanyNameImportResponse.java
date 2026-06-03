package com.emrehalli.financeportal.company.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CompanyNameImportResponse {
    private int checked;
    private int updated;
    private int skippedAlreadyGood;
    private List<String> missingProfiles;
    private List<String> updatedTickers;
    private List<String> errors;
}
