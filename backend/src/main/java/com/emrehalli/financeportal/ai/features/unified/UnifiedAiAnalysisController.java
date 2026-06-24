package com.emrehalli.financeportal.ai.features.unified;

import com.emrehalli.financeportal.ai.core.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.core.access.AiFeatureType;
import com.emrehalli.financeportal.ai.features.unified.UnifiedAiAnalysisService;
import com.emrehalli.financeportal.ai.features.unified.UnifiedAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Birleşik Analiz", description = "AI destekli birleşik analiz")
@RestController
@RequestMapping("/api/v1/ai")
public class UnifiedAiAnalysisController {

    private final UnifiedAiAnalysisService   unifiedAiAnalysisService;
    private final AiFeatureAccessService     featureAccessService;

    public UnifiedAiAnalysisController(UnifiedAiAnalysisService unifiedAiAnalysisService,
                                       AiFeatureAccessService featureAccessService) {
        this.unifiedAiAnalysisService = unifiedAiAnalysisService;
        this.featureAccessService     = featureAccessService;
    }

    @Operation(summary = "AI birleşik analiz")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Birleşik analiz başarıyla döndürüldü"))
    @GetMapping("/unified/{symbol}")
    public ResponseEntity<UnifiedAnalysisResponse> getUnifiedAnalysis(
            @PathVariable String symbol,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "tr") String language) {
        featureAccessService.logAccess(AiFeatureType.UNIFIED_ANALYSIS);
        return ResponseEntity.ok(unifiedAiAnalysisService.getUnifiedAnalysis(symbol, type, language));
    }
}

