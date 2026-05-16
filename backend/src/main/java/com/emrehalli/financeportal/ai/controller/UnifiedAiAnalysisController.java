package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.access.AiFeatureType;
import com.emrehalli.financeportal.ai.unified.UnifiedAiAnalysisService;
import com.emrehalli.financeportal.ai.unified.UnifiedAnalysisResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * GET /api/v1/ai/unified/{symbol}?type=STOCK  [PREMIUM]
     *
     * Returns a single premium unified analysis that merges technical and
     * (for STOCK instruments) fundamental insights.
     * Cache key: ai:unified:{SYMBOL}, TTL 12 h from LLM / 30 min from fallback.
     */
    @GetMapping("/unified/{symbol}")
    public ResponseEntity<UnifiedAnalysisResponse> getUnifiedAnalysis(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "STOCK") String type) {
        featureAccessService.logAccess(AiFeatureType.UNIFIED_ANALYSIS);
        return ResponseEntity.ok(unifiedAiAnalysisService.getUnifiedAnalysis(symbol, type));
    }
}
