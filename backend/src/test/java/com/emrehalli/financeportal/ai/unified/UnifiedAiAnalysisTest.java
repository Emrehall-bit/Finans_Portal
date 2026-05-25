package com.emrehalli.financeportal.ai.unified;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.postprocess.AiDisclaimerCleaner;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.postprocess.TurkishFinancialTextCleaner;
import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiFundamentalAnalysisService;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
import com.emrehalli.financeportal.ai.service.AiTechnicalAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnifiedAiAnalysisTest {

    private final UnifiedInsightAssembler assembler = new UnifiedInsightAssembler();
    private final UnifiedAnalysisPromptBuilder promptBuilder = new UnifiedAnalysisPromptBuilder();
    private final AiResponsePostProcessor postProcessor =
            new AiResponsePostProcessor(new TurkishFinancialTextCleaner(), new AiDisclaimerCleaner());

    private AiTechnicalAnalysisService technicalService;
    private AiFundamentalAnalysisService fundamentalService;
    private AiGatewayService aiGatewayService;
    private AiResponseCacheService cacheService;

    private UnifiedAiAnalysisService service;

    @BeforeEach
    void setUp() {
        technicalService = mock(AiTechnicalAnalysisService.class);
        fundamentalService = mock(AiFundamentalAnalysisService.class);
        aiGatewayService = mock(AiGatewayService.class);
        cacheService = mock(AiResponseCacheService.class);

        service = new UnifiedAiAnalysisService(
                technicalService,
                fundamentalService,
                assembler,
                promptBuilder,
                aiGatewayService,
                cacheService,
                postProcessor,
                new ObjectMapper(),
                mock(AiResponseLogHelper.class)
        );
    }

    @Test
    void getUnifiedAnalysis_usesCorrectCacheKey() {
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(UnifiedAnalysisResponse.class), any()))
                .thenReturn(new AiResponseCacheService.LookupResult<>(
                        new UnifiedAnalysisResponse(
                                "THYAO",
                                "Summary",
                                List.of(),
                                List.of(),
                                "groq",
                                false,
                                AiResponseMetadata.fromAiResponse(new AiResponse("{}", AiProviderType.GROQ, false, "llama3", 1L), "COMPLETE")
                        ),
                        false
                ));

        service.getUnifiedAnalysis("thyao", "STOCK");

        verify(cacheService).getOrComputeWithDynamicTtlStatus(
                eq("ai:unified:THYAO"),
                eq(UnifiedAnalysisResponse.class),
                any()
        );
    }

    @Test
    void serviceReturnsMetadataWhenLlmSucceeds() {
        String llmJson = """
                {
                  "summary": "Test ozeti.",
                  "highlights": ["Kalem 1", "Kalem 2"],
                  "risks": ["Risk 1"]
                }
                """;

        when(technicalService.getTechnicalComment("THYAO")).thenReturn(makeTech());
        when(fundamentalService.getFundamentalComment("THYAO")).thenReturn(makeFund());
        when(aiGatewayService.generate(eq(AiTaskType.PAGE_ANALYSIS), anyString()))
                .thenReturn(Optional.of(new AiResponse(llmJson, AiProviderType.GROQ, false, "llama3", 500L)));
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(UnifiedAnalysisResponse.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = (java.util.function.Supplier<?>) invocation.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return new AiResponseCacheService.LookupResult<>(cached.value(), false);
                });

        UnifiedAnalysisResponse result = service.getUnifiedAnalysis("THYAO", "STOCK");

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().providerUsed()).isEqualTo("GROQ");
        assertThat(result.metadata().modelUsed()).isEqualTo("llama3");
        assertThat(result.metadata().aiEnhanced()).isTrue();
        assertThat(result.metadata().deterministicFallbackUsed()).isFalse();
    }

    private AiTechnicalAnalysisResponse makeTech() {
        return new AiTechnicalAnalysisResponse(
                "THYAO",
                "Teknik ozet.",
                "Trend yorumu.",
                "Momentum yorumu.",
                AiTechnicalAnalysisResponse.RiskLevel.MEDIUM,
                AiTechnicalAnalysisResponse.AiSignal.POSITIVE,
                "Bu yorum yatirim tavsiyesi degildir.",
                AiResponseMetadata.deterministic("FULL")
        );
    }

    private AiFundamentalAnalysisResponse makeFund() {
        return new AiFundamentalAnalysisResponse(
                "THYAO",
                "Finansal ozet.",
                List.of("Guclu taraf"),
                List.of("Zayif taraf"),
                List.of("Risk"),
                "Buyume yorumu.",
                AiFundamentalAnalysisResponse.FinancialHealth.STABLE,
                "Bu yorum yatirim tavsiyesi degildir.",
                AiResponseMetadata.deterministic("FULL")
        );
    }
}



