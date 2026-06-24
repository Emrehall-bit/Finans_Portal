package com.emrehalli.financeportal.ai.features.technical;

import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Teknik Analiz", description = "AI destekli teknik analiz")
@RestController
@RequestMapping("/api/v1/ai")
public class AiTechnicalAnalysisController {

    private final AiTechnicalAnalysisService aiTechnicalAnalysisService;

    public AiTechnicalAnalysisController(AiTechnicalAnalysisService aiTechnicalAnalysisService) {
        this.aiTechnicalAnalysisService = aiTechnicalAnalysisService;
    }

    @Operation(summary = "AI teknik analiz")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI teknik analiz yanıtı başarıyla döndürüldü"))
    @GetMapping("/technical/{symbol}")
    public AiTechnicalAnalysisResponse getTechnicalAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "tr") String language) {
        return aiTechnicalAnalysisService.getTechnicalComment(symbol, language);
    }
}
