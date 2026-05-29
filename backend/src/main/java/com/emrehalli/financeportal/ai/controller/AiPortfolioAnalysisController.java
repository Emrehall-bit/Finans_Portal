package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.access.AiFeatureType;
import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisResponse;
import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/portfolio-analysis/{portfolioId}")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioAnalysis(@PathVariable Long portfolioId) {
        featureAccessService.logAccess(AiFeatureType.PORTFOLIO_AI);
        return ResponseEntity.ok(portfolioAnalysisService.getPortfolioAnalysis(portfolioId));
    }
}




