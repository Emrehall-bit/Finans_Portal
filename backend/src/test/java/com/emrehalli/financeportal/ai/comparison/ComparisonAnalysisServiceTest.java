package com.emrehalli.financeportal.ai.comparison;

import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.postprocess.AiDisclaimerCleaner;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.postprocess.TurkishFinancialTextCleaner;
import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
import com.emrehalli.financeportal.company.dto.response.CompanyFundamentalsResponse;
import com.emrehalli.financeportal.company.service.CompanyQueryService;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComparisonAnalysisServiceTest {

    private TechnicalAnalysisService technicalAnalysisService;
    private CompanyQueryService companyQueryService;
    private ComparisonAnalysisPromptBuilder promptBuilder;
    private AiGatewayService aiGatewayService;
    private AiResponseCacheService cacheService;

    private ComparisonAnalysisService service;

    @BeforeEach
    void setUp() {
        technicalAnalysisService = mock(TechnicalAnalysisService.class);
        companyQueryService = mock(CompanyQueryService.class);
        promptBuilder = mock(ComparisonAnalysisPromptBuilder.class);
        aiGatewayService = mock(AiGatewayService.class);
        cacheService = mock(AiResponseCacheService.class);

        AiResponsePostProcessor postProcessor =
                new AiResponsePostProcessor(new TurkishFinancialTextCleaner(), new AiDisclaimerCleaner());

        service = new ComparisonAnalysisService(
                technicalAnalysisService,
                companyQueryService,
                promptBuilder,
                aiGatewayService,
                cacheService,
                postProcessor,
                new ObjectMapper(),
                mock(AiResponseLogHelper.class)
        );
    }

    @Test
    void cacheKey_isNormalizedIndependentlyOfRequestOrder() {
        ComparisonAnalysisResponse canned = new ComparisonAnalysisResponse(
                "PGSUS", "THYAO", "summary", "technical", "fundamental", "risk",
                List.of(), List.of(), List.of(), List.of(), "final", DataQuality.PARTIAL, null, false,
                AiResponseMetadata.deterministic(DataQuality.PARTIAL.name())
        );
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(ComparisonAnalysisResponse.class), any()))
                .thenReturn(new AiResponseCacheService.LookupResult<>(canned, false));

        service.getComparisonAnalysis("thyao", "pgsus");
        service.getComparisonAnalysis("PGSUS", "THYAO");

        verify(cacheService, times(2)).getOrComputeWithDynamicTtlStatus(eq("ai:comparison-analysis:PGSUS-THYAO"), eq(ComparisonAnalysisResponse.class), any());
    }

    @Test
    void missingData_usesDeterministicFallback() {
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(ComparisonAnalysisResponse.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = (java.util.function.Supplier<?>) invocation.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return new AiResponseCacheService.LookupResult<>(cached.value(), false);
                });
        when(technicalAnalysisService.analyze(anyString(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("no technical data"));
        when(companyQueryService.getFundamentals(anyString()))
                .thenThrow(new ResourceNotFoundException("no fundamentals"));
        when(aiGatewayService.generate(eq(AiTaskType.COMPANY_COMPARISON), anyString()))
                .thenReturn(Optional.empty());

        ComparisonAnalysisResponse result = service.getComparisonAnalysis("BTC", "ETH");

        assertThat(result.metadata().deterministicFallbackUsed()).isTrue();
        assertThat(result.metadata().aiEnhanced()).isFalse();
        assertThat(result.dataQuality()).isEqualTo(DataQuality.LIMITED);
        assertThat(result.summary()).contains("BTC").contains("ETH");
    }

    @Test
    void groqPrimaryResponse_isUsedWhenAvailable() {
        stubCompleteDeterministicInputs();
        when(aiGatewayService.generate(eq(AiTaskType.COMPANY_COMPARISON), anyString()))
                .thenReturn(Optional.of(new AiResponse("""
                        {
                          "summary": "THYAO kisa vadede daha dengeli.",
                          "technicalComparison": "THYAO teknik tarafta daha iyi.",
                          "fundamentalComparison": "Iki tarafta da temel veri var.",
                          "riskComparison": "PGSUS riski daha yuksek.",
                          "strengthsLeft": ["Trend daha temiz."],
                          "strengthsRight": ["Marj toparlanabilir."],
                          "weaknessesLeft": ["F/K baskisi var."],
                          "weaknessesRight": ["Momentum daha dalgali."],
                          "finalComment": "THYAO tarafi su an daha dengeli gorunuyor."
                        }
                        """, AiProviderType.GROQ, false, "llama", 320L)));

        ComparisonAnalysisResponse result = invokeThroughCache("THYAO", "PGSUS");

        assertThat(result.providerUsed()).isEqualTo("groq");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.summary()).contains("THYAO");
    }

    @Test
    void geminiFallbackResponse_metadataIsPreserved() {
        stubCompleteDeterministicInputs();
        when(aiGatewayService.generate(eq(AiTaskType.COMPANY_COMPARISON), anyString()))
                .thenReturn(Optional.of(new AiResponse("""
                        {
                          "summary": "Iki taraf arasinda farklar var.",
                          "technicalComparison": "Teknik sinyaller ayrisiyor.",
                          "fundamentalComparison": "Temel tarafta sol taraf daha guclu.",
                          "riskComparison": "Sag taraf daha kirilgan.",
                          "strengthsLeft": ["ROE daha iyi."],
                          "strengthsRight": ["Kisa vadeli fiyat hareketi daha hizli."],
                          "weaknessesLeft": ["Degerleme daha pahali olabilir."],
                          "weaknessesRight": ["Risk seviyesi daha yuksek."],
                          "finalComment": "Veriler sol tarafi daha dengeli gosteriyor."
                        }
                        """, AiProviderType.GEMINI, true, "gemini", 410L)));

        ComparisonAnalysisResponse result = invokeThroughCache("THYAO", "PGSUS");

        assertThat(result.providerUsed()).isEqualTo("gemini");
        assertThat(result.fallbackUsed()).isTrue();
    }

    private ComparisonAnalysisResponse invokeThroughCache(String left, String right) {
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(ComparisonAnalysisResponse.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = (java.util.function.Supplier<?>) invocation.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return new AiResponseCacheService.LookupResult<>(cached.value(), false);
                });
        when(promptBuilder.build(any())).thenReturn("prompt");
        return service.getComparisonAnalysis(left, right);
    }

    private void stubCompleteDeterministicInputs() {
        when(technicalAnalysisService.analyze(eq("THYAO"), any(), any(), anyString()))
                .thenReturn(technicalResult("THYAO", TrendDirection.UPTREND, BigDecimal.valueOf(58), List.of(TechnicalSignal.PRICE_ABOVE_SMA20)));
        when(technicalAnalysisService.analyze(eq("PGSUS"), any(), any(), anyString()))
                .thenReturn(technicalResult("PGSUS", TrendDirection.SIDEWAYS, BigDecimal.valueOf(49), List.of()));

        when(companyQueryService.getFundamentals("THYAO")).thenReturn(fundamentals("THYAO", BigDecimal.valueOf(0.18), BigDecimal.valueOf(0.08), BigDecimal.valueOf(0.6)));
        when(companyQueryService.getFundamentals("PGSUS")).thenReturn(fundamentals("PGSUS", BigDecimal.valueOf(0.11), BigDecimal.valueOf(0.04), BigDecimal.valueOf(1.4)));
        when(companyQueryService.getFinancials(anyString())).thenReturn(List.of());
    }

    private TechnicalAnalysisResult technicalResult(String symbol,
                                                    TrendDirection trendDirection,
                                                    BigDecimal rsi,
                                                    List<TechnicalSignal> signals) {
        return new TechnicalAnalysisResult(
                symbol,
                LocalDate.now().minusDays(180),
                LocalDate.now(),
                BigDecimal.valueOf(100),
                "AVAILABLE",
                null,
                trendDirection,
                signals,
                Map.of(
                        IndicatorType.RSI14, rsi,
                        IndicatorType.SMA20, BigDecimal.valueOf(95),
                        IndicatorType.SMA50, BigDecimal.valueOf(90)
                ),
                List.of(new TechnicalAnalysisResult.Point(
                        LocalDate.now(),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(98),
                        BigDecimal.valueOf(95),
                        BigDecimal.valueOf(90),
                        rsi
                ))
        );
    }

    private CompanyFundamentalsResponse fundamentals(String symbol,
                                                     BigDecimal roe,
                                                     BigDecimal netMargin,
                                                     BigDecimal debtToEquity) {
        return CompanyFundamentalsResponse.builder()
                .tickerCode(symbol)
                .companyName(symbol + " A.S.")
                .roe(roe)
                .roa(BigDecimal.valueOf(0.05))
                .grossMargin(BigDecimal.valueOf(0.20))
                .netMargin(netMargin)
                .revenueGrowth(BigDecimal.valueOf(0.12))
                .netProfitGrowth(BigDecimal.valueOf(0.10))
                .peRatio(BigDecimal.valueOf(12))
                .pbRatio(BigDecimal.valueOf(1.6))
                .debtToEquity(debtToEquity)
                .build();
    }
}




