package com.emrehalli.financeportal.ai.features.technical;

import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiTechnicalAnalysisController {

    private final AiTechnicalAnalysisService aiTechnicalAnalysisService;

    public AiTechnicalAnalysisController(AiTechnicalAnalysisService aiTechnicalAnalysisService) {
        this.aiTechnicalAnalysisService = aiTechnicalAnalysisService;
    }

    @GetMapping("/technical/{symbol}")
    public AiTechnicalAnalysisResponse getTechnicalAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "tr") String language) {
        return aiTechnicalAnalysisService.getTechnicalComment(symbol, language);
    }
}
