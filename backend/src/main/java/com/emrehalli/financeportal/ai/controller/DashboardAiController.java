package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.access.AiFeatureType;
import com.emrehalli.financeportal.ai.dashboard.DashboardAiResponse;
import com.emrehalli.financeportal.ai.dashboard.DashboardAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/ai")
public class DashboardAiController {

    private final DashboardAiService dashboardAiService;
    private final AiFeatureAccessService featureAccessService;

    public DashboardAiController(DashboardAiService dashboardAiService,
                                 AiFeatureAccessService featureAccessService) {
        this.dashboardAiService = dashboardAiService;
        this.featureAccessService = featureAccessService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardAiResponse> getDashboardAnalysis(
            @RequestParam(defaultValue = "tr") String language,
            @RequestParam(defaultValue = "0") BigDecimal avgMarketChange,
            @RequestParam(defaultValue = "0") int gainerCount,
            @RequestParam(defaultValue = "0") int loserCount,
            @RequestParam(defaultValue = "0") int totalQuotes) {

        featureAccessService.logAccess(AiFeatureType.DASHBOARD_AI);
        DashboardAiResponse response = dashboardAiService.analyze(
                language, avgMarketChange, gainerCount, loserCount, totalQuotes);
        return ResponseEntity.ok(response);
    }
}
