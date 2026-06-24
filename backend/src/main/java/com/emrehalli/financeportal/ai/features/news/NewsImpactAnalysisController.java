package com.emrehalli.financeportal.ai.features.news;

import com.emrehalli.financeportal.ai.core.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.core.access.AiFeatureType;
import com.emrehalli.financeportal.ai.features.news.NewsImpactAnalysisService;
import com.emrehalli.financeportal.ai.features.news.NewsImpactResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Haber Etki Analizi", description = "AI destekli haber etki analizi")
@RestController
@RequestMapping("/api/v1/ai")
public class NewsImpactAnalysisController {

    private final NewsImpactAnalysisService newsImpactAnalysisService;
    private final AiFeatureAccessService    featureAccessService;

    public NewsImpactAnalysisController(NewsImpactAnalysisService newsImpactAnalysisService,
                                        AiFeatureAccessService featureAccessService) {
        this.newsImpactAnalysisService = newsImpactAnalysisService;
        this.featureAccessService      = featureAccessService;
    }

    @Operation(summary = "Haber etki analizi")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Haber etki analizi başarıyla döndürüldü"))
    @GetMapping("/news-impact/{newsId}")
    public ResponseEntity<NewsImpactResponse> getNewsImpactAnalysis(
            @PathVariable Long newsId,
            @RequestParam(defaultValue = "tr") String language) {
        featureAccessService.logAccess(AiFeatureType.NEWS_IMPACT_ANALYSIS);
        return ResponseEntity.ok(newsImpactAnalysisService.getNewsImpactAnalysis(newsId, language));
    }
}

