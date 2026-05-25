package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.access.AiFeatureType;
import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisResponse;
import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiComparisonAnalysisController {

    private final ComparisonAnalysisService comparisonAnalysisService;
    private final AiFeatureAccessService featureAccessService;

    public AiComparisonAnalysisController(ComparisonAnalysisService comparisonAnalysisService,
                                          AiFeatureAccessService featureAccessService) {
        this.comparisonAnalysisService = comparisonAnalysisService;
        this.featureAccessService = featureAccessService;
    }

    @GetMapping("/compare-analysis")
    public ResponseEntity<ComparisonAnalysisResponse> getComparisonAnalysis(
            @RequestParam String left,
            @RequestParam String right) {
        featureAccessService.logAccess(AiFeatureType.COMPANY_COMPARISON_AI);
        return ResponseEntity.ok(comparisonAnalysisService.getComparisonAnalysis(left, right));
    }
}



