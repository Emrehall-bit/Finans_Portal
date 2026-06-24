package com.emrehalli.financeportal.ai.features.comparison;

import com.emrehalli.financeportal.ai.core.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.core.access.AiFeatureType;
import com.emrehalli.financeportal.ai.features.comparison.ComparisonAnalysisResponse;
import com.emrehalli.financeportal.ai.features.comparison.ComparisonAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Karşılaştırma", description = "AI destekli enstrüman karşılaştırma analizi")
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

    @Operation(summary = "AI karşılaştırma analizi")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Karşılaştırma analizi başarıyla döndürüldü"))
    @GetMapping("/compare-analysis")
    public ResponseEntity<ComparisonAnalysisResponse> getComparisonAnalysis(
            @RequestParam String left,
            @RequestParam String right,
            @RequestParam(defaultValue = "tr") String language) {
        featureAccessService.logAccess(AiFeatureType.COMPANY_COMPARISON_AI);
        return ResponseEntity.ok(comparisonAnalysisService.getComparisonAnalysis(left, right, language));
    }
}

