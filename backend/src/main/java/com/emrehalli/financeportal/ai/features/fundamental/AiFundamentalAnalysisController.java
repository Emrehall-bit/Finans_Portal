package com.emrehalli.financeportal.ai.features.fundamental;

import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiFundamentalAnalysisController {

    private final AiFundamentalAnalysisService aiFundamentalAnalysisService;

    public AiFundamentalAnalysisController(AiFundamentalAnalysisService aiFundamentalAnalysisService) {
        this.aiFundamentalAnalysisService = aiFundamentalAnalysisService;
    }

    @GetMapping("/fundamental/{symbol}")
    public AiFundamentalAnalysisResponse getFundamentalAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "tr") String language) {
        return aiFundamentalAnalysisService.getFundamentalComment(symbol, language);
    }
}
