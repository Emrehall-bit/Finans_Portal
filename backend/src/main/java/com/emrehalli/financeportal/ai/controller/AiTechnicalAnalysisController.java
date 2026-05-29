package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.service.AiTechnicalAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiTechnicalAnalysisController {

    private final AiTechnicalAnalysisService aiTechnicalAnalysisService;

    public AiTechnicalAnalysisController(AiTechnicalAnalysisService aiTechnicalAnalysisService) {
        this.aiTechnicalAnalysisService = aiTechnicalAnalysisService;
    }

    @GetMapping("/technical/{symbol}")
    public AiTechnicalAnalysisResponse getTechnicalAnalysis(@PathVariable String symbol) {
        return aiTechnicalAnalysisService.getTechnicalComment(symbol);
    }
}




