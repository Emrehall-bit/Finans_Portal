package com.emrehalli.financeportal.ai.features.portfolio;

import com.emrehalli.financeportal.ai.core.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.core.access.AiFeatureType;
import com.emrehalli.financeportal.ai.features.portfolio.PortfolioAnalysisResponse;
import com.emrehalli.financeportal.ai.features.portfolio.PortfolioAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Portföy Analizi", description = "AI destekli portföy analizi")
@RestController
@RequestMapping("/api/v1/ai")
public class AiPortfolioAnalysisController {

    private final PortfolioAnalysisService portfolioAnalysisService;
    private final AiFeatureAccessService featureAccessService;

    public AiPortfolioAnalysisController(PortfolioAnalysisService portfolioAnalysisService,
                                         AiFeatureAccessService featureAccessService) {
        this.portfolioAnalysisService = portfolioAnalysisService;
        this.featureAccessService = featureAccessService;
    }

    @Operation(summary = "AI portföy analizi")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portföy analizi başarıyla döndürüldü"))
    @GetMapping("/portfolio-analysis/{portfolioId}")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioAnalysis(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "tr") String language) {
        featureAccessService.logAccess(AiFeatureType.PORTFOLIO_AI);
        return ResponseEntity.ok(portfolioAnalysisService.getPortfolioAnalysis(portfolioId, language));
    }
}

