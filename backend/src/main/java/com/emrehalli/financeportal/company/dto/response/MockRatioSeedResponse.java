package com.emrehalli.financeportal.company.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MockRatioSeedResponse {

    private int autoCreatedProfiles;

    private int createdMockRatios;

    private int skippedExistingRatios;

    private List<String> errors;
}

