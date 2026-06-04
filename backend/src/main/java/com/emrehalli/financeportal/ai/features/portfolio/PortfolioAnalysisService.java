package com.emrehalli.financeportal.ai.features.portfolio;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import com.emrehalli.financeportal.ai.core.provider.AiResponse;
import com.emrehalli.financeportal.ai.core.provider.AiTaskType;
import com.emrehalli.financeportal.ai.core.gateway.AiGatewayService;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.core.gateway.AiResponseLogHelper;
import com.emrehalli.financeportal.ai.core.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import com.emrehalli.financeportal.portfolio.service.PortfolioService;
import com.emrehalli.financeportal.portfolio.service.PortfolioValuationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

@Service
public class PortfolioAnalysisService {

    private static final Logger logger = LogManager.getLogger(PortfolioAnalysisService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final PortfolioService portfolioService;
    private final PortfolioHoldingService portfolioHoldingService;
    private final PortfolioAnalysisPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final AiResponseCacheService cacheService;
    private final AiResponseLogHelper responseLogHelper;
    private final PortfolioAiContextBuilder contextBuilder;
    private final PortfolioFallbackBuilder fallbackBuilder;
    private final PortfolioResponseParser responseParser;

    public PortfolioAnalysisService(PortfolioService portfolioService,
                                    PortfolioHoldingService portfolioHoldingService,
                                    PortfolioAnalysisPromptBuilder promptBuilder,
                                    AiGatewayService aiGatewayService,
                                    AiResponseCacheService cacheService,
                                    AiResponsePostProcessor postProcessor,
                                    ObjectMapper objectMapper,
                                    AiResponseLogHelper responseLogHelper) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingService = portfolioHoldingService;
        this.promptBuilder = promptBuilder;
        this.aiGatewayService = aiGatewayService;
        this.cacheService = cacheService;
        this.responseLogHelper = responseLogHelper;
        PortfolioWeightCalculator weightCalculator = new PortfolioWeightCalculator();
        this.contextBuilder = new PortfolioAiContextBuilder(weightCalculator);
        this.fallbackBuilder = new PortfolioFallbackBuilder();
        this.responseParser = new PortfolioResponseParser(postProcessor, objectMapper);
    }

    public PortfolioAnalysisResponse getPortfolioAnalysis(Long portfolioId) {
        return getPortfolioAnalysis(portfolioId, "tr");
    }

    public PortfolioAnalysisResponse getPortfolioAnalysis(Long portfolioId, String language) {
        Portfolio portfolio = portfolioService.getPortfolioEntityById(portfolioId);
        PortfolioValuationResult valuation = portfolioHoldingService.getPortfolioValuation(portfolioId);
        List<PortfolioHoldingDto> holdings = valuation.holdings();
        PortfolioSummaryResponse summary = valuation.summary();
        String lang = AiPromptBuilder.normLang(language);
        String cacheKey = buildCacheKey(portfolio, holdings) + ":" + lang;

        try {
            LookupResult<PortfolioAnalysisResponse> lookup = cacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey,
                    PortfolioAnalysisResponse.class,
                    () -> compute(portfolio, holdings, summary, lang)
            );
            PortfolioAnalysisResponse response = fallbackBuilder.withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.PORTFOLIO_ANALYSIS, response.metadata());
            return response;
        } catch (Exception exception) {
            logger.warn("AI portfolio endpoint critical failure. portfolioId={}, reason={}", portfolioId, exception.getMessage());
            PortfolioAnalysisResponse response = fallbackBuilder.deterministicFallback(contextBuilder.build(portfolio, holdings, summary));
            responseLogHelper.log(AiTaskType.PORTFOLIO_ANALYSIS, response.metadata());
            return response;
        }
    }

    String buildCacheKey(Portfolio portfolio, List<PortfolioHoldingDto> holdings) {
        return "ai:portfolio-analysis:" + portfolio.getId() + ":" + computeHoldingsHash(portfolio, holdings);
    }

    private CachedValue<PortfolioAnalysisResponse> compute(Portfolio portfolio,
                                                           List<PortfolioHoldingDto> holdings,
                                                           PortfolioSummaryResponse summary,
                                                           String lang) {
        PortfolioAnalysisContext context = contextBuilder.build(portfolio, holdings, summary);
        PortfolioAnalysisResponse fallback = fallbackBuilder.deterministicFallback(context);

        if (holdings.isEmpty()) {
            return new CachedValue<>(fallback, CACHE_TTL);
        }

        String prompt = promptBuilder.build(context, lang);
        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.PORTFOLIO_ANALYSIS, prompt);
        if (aiResponse.isEmpty()) {
            logger.warn("AI portfolio generation unavailable. portfolioId={}", portfolio.getId());
            return new CachedValue<>(fallback, CACHE_TTL);
        }

        try {
            PortfolioAnalysisResponse response = responseParser.parse(aiResponse.get(), fallback);
            logger.info("AI portfolio computed. portfolioId={}, provider={}, fallbackUsed={}",
                    portfolio.getId(), response.providerUsed(), response.fallbackUsed());
            return new CachedValue<>(response, CACHE_TTL);
        } catch (Exception exception) {
            logger.warn("AI portfolio parse failed. portfolioId={}, reason={}", portfolio.getId(), exception.getMessage());
            return new CachedValue<>(fallback, CACHE_TTL);
        }
    }

    private String computeHoldingsHash(Portfolio portfolio, List<PortfolioHoldingDto> holdings) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringJoiner joiner = new StringJoiner("|");
            joiner.add(String.valueOf(portfolio.getId()));
            joiner.add(nullSafe(portfolio.getPortfolioName()));
            holdings.stream()
                    .sorted(Comparator.comparing(PortfolioHoldingDto::getHoldingId))
                    .forEach(holding -> joiner
                            .add(String.valueOf(holding.getHoldingId()))
                            .add(nullSafe(holding.getInstrumentCode()))
                            .add(nullSafeDecimal(holding.getQuantity()))
                            .add(nullSafeDecimal(holding.getBuyPrice()))
                            .add(String.valueOf(holding.getUpdatedAt()))
                    );
            byte[] hash = digest.digest(joiner.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception exception) {
            logger.warn("Portfolio AI cache hash fallback used. portfolioId={}, reason={}", portfolio.getId(), exception.getMessage());
            return String.valueOf(holdings.size());
        }
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private String nullSafeDecimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
