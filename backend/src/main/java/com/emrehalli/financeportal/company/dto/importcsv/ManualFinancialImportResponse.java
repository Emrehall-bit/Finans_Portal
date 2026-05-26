package com.emrehalli.financeportal.company.dto.importcsv;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManualFinancialImportResponse {

    private boolean dryRun;
    private int createdReports;
    private int updatedReports;
    private int createdValues;
    private int updatedValues;
    private int deletedStaleValues;
    private List<String> recalculatedTickers;
    private List<String> preservedRealRatios;
    private List<String> createdMockRatios;
    private List<String> skippedExisting;
    private List<String> updatedShareCounts;
    private List<String> skippedShareCounts;
    private List<String> missingShareCountWarnings;
    private List<ManualFinancialImportError> validationErrors;
}



