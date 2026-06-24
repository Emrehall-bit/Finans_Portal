package com.emrehalli.financeportal.company.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ShareCountImportResponse {

    private List<String> updatedShareCounts;

    private List<String> skippedExisting;

    private List<String> notFoundTickers;

    private List<ShareCountImportRowError> invalidRows;

    private List<String> recalculatedTickers;
}

