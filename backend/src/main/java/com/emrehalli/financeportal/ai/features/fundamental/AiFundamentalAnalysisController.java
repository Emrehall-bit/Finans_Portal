package com.emrehalli.financeportal.ai.features.fundamental;

import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Temel Analiz", description = "AI destekli temel analiz")
@RestController
@RequestMapping("/api/v1/ai")
public class AiFundamentalAnalysisController {

    private final AiFundamentalAnalysisService aiFundamentalAnalysisService;

    public AiFundamentalAnalysisController(AiFundamentalAnalysisService aiFundamentalAnalysisService) {
        this.aiFundamentalAnalysisService = aiFundamentalAnalysisService;
    }

    @Operation(summary = "AI temel analiz")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI temel analiz yanıtı başarıyla döndürüldü"))
    @GetMapping("/fundamental/{symbol}")
    public AiFundamentalAnalysisResponse getFundamentalAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "tr") String language) {
        return aiFundamentalAnalysisService.getFundamentalComment(symbol, language);
    }
}
