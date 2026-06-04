package com.emrehalli.financeportal.ai.features.unified;

import com.emrehalli.financeportal.ai.core.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.core.access.AiFeatureType;
import com.emrehalli.financeportal.ai.features.unified.UnifiedAiAnalysisService;
import com.emrehalli.financeportal.ai.features.unified.UnifiedAnalysisResponse;
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
     * Returns a unified analysis combining technical and (for STOCK) fundamental insights.
     * The {@code type} parameter is required from the caller — omitting it results in a
     * not-applicable response rather than silently defaulting to STOCK.
     * Cache key: ai:unified:{SYMBOL}, TTL 12 h from LLM / 30 min from fallback.
     */
    @GetMapping("/unified/{symbol}")
    public ResponseEntity<UnifiedAnalysisResponse> getUnifiedAnalysis(
            @PathVariable String symbol,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "tr") String language) {
        featureAccessService.logAccess(AiFeatureType.UNIFIED_ANALYSIS);
        return ResponseEntity.ok(unifiedAiAnalysisService.getUnifiedAnalysis(symbol, type, language));
    }
}




