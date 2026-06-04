package com.emrehalli.financeportal.ai.comparison;

import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
import com.emrehalli.financeportal.company.service.CompanyQueryService;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ComparisonAnalysisService {

    private static final Logger logger = LogManager.getLogger(ComparisonAnalysisService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final ComparisonAnalysisPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final AiResponseCacheService cacheService;
    private final AiResponseLogHelper responseLogHelper;
    private final ComparisonContextBuilder contextBuilder;
    private final ComparisonFallbackBuilder fallbackBuilder;
    private final ComparisonResponseParser responseParser;

    public ComparisonAnalysisService(TechnicalAnalysisService technicalAnalysisService,
                                     CompanyQueryService companyQueryService,
                                     ComparisonAnalysisPromptBuilder promptBuilder,
                                     AiGatewayService aiGatewayService,
                                     AiResponseCacheService cacheService,
                                     AiResponsePostProcessor postProcessor,
                                     ObjectMapper objectMapper,
                                     AiResponseLogHelper responseLogHelper) {
        this.promptBuilder = promptBuilder;
        this.aiGatewayService = aiGatewayService;
        this.cacheService = cacheService;
        this.responseLogHelper = responseLogHelper;
        this.contextBuilder = new ComparisonContextBuilder(technicalAnalysisService, companyQueryService);
        this.fallbackBuilder = new ComparisonFallbackBuilder();
        this.responseParser = new ComparisonResponseParser(postProcessor, objectMapper);
    }

    public ComparisonAnalysisResponse getComparisonAnalysis(String leftSymbol, String rightSymbol) {
        return getComparisonAnalysis(leftSymbol, rightSymbol, "tr");
    }

    public ComparisonAnalysisResponse getComparisonAnalysis(String leftSymbol, String rightSymbol, String language) {
        String left = normalizeSymbol(leftSymbol);
        String right = normalizeSymbol(rightSymbol);
        validateSymbols(left, right);
        String lang = AiPromptBuilder.normLang(language);
        String normalizedPair = normalizedPair(left, right);
        String cacheKey = "ai:comparison-analysis:" + normalizedPair + ":" + lang;

        try {
            LookupResult<ComparisonAnalysisResponse> lookup = cacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey,
                    ComparisonAnalysisResponse.class,
                    () -> compute(left, right, lang)
            );
            ComparisonAnalysisResponse response = fallbackBuilder.withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.COMPANY_COMPARISON, response.metadata());
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            logger.warn("AI comparison endpoint critical failure. pair={}, reason={}", normalizedPair, exception.getMessage());
            ComparisonAnalysisResponse response = fallbackBuilder.deterministicFallback(
                    contextBuilder.buildUnavailableSnapshot(left),
                    contextBuilder.buildUnavailableSnapshot(right),
                    DataQuality.LIMITED
            );
            responseLogHelper.log(AiTaskType.COMPANY_COMPARISON, response.metadata());
            return response;
        }
    }

    String normalizedPair(String left, String right) {
        return List.of(normalizeSymbol(left), normalizeSymbol(right)).stream()
                .sorted(Comparator.naturalOrder())
                .reduce((a, b) -> a + "-" + b)
                .orElse("-");
    }

    private CachedValue<ComparisonAnalysisResponse> compute(String left, String right, String lang) {
        ComparisonAnalysisContext context = contextBuilder.build(left, right);
        ComparisonAnalysisResponse fallback = fallbackBuilder.deterministicFallback(context);

        String prompt = promptBuilder.build(context, lang);
        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.COMPANY_COMPARISON, prompt);
        if (aiResponse.isEmpty()) {
            logger.warn("AI comparison generation unavailable. pair={}-{}", left, right);
            return new CachedValue<>(fallback, CACHE_TTL);
        }

        try {
            ComparisonAnalysisResponse response = responseParser.parse(aiResponse.get(), fallback);
            logger.info("AI comparison computed. left={}, right={}, provider={}, fallbackUsed={}",
                    left, right, response.providerUsed(), response.fallbackUsed());
            return new CachedValue<>(response, CACHE_TTL);
        } catch (Exception exception) {
            logger.warn("AI comparison parse failed. left={}, right={}, reason={}", left, right, exception.getMessage());
            return new CachedValue<>(fallback, CACHE_TTL);
        }
    }

    private void validateSymbols(String left, String right) {
        if (left.equals("-") || right.equals("-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both symbols are required");
        }
        if (left.equals(right)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two different symbols are required");
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "-";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
