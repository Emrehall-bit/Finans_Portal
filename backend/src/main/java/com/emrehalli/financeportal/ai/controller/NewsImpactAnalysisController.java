package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.access.AiFeatureType;
import com.emrehalli.financeportal.ai.news.NewsImpactAnalysisService;
import com.emrehalli.financeportal.ai.news.NewsImpactResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * GET /api/v1/ai/news-impact/{newsId}  [PREMIUM]
     *
     * Returns AI-powered financial impact analysis for a news item.
     * Returns 404 if the news item does not exist.
     * Cache key: ai:news-impact:{newsId}, TTL 24 h (LLM) / 12 h (fallback).
     */
    @GetMapping("/news-impact/{newsId}")
    public ResponseEntity<NewsImpactResponse> getNewsImpactAnalysis(
            @PathVariable Long newsId,
            @RequestParam(defaultValue = "tr") String language) {
        featureAccessService.logAccess(AiFeatureType.NEWS_IMPACT_ANALYSIS);
        return ResponseEntity.ok(newsImpactAnalysisService.getNewsImpactAnalysis(newsId, language));
    }
}




