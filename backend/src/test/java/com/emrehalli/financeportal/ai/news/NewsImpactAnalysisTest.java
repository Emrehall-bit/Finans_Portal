package com.emrehalli.financeportal.ai.news;

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
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsImpactAnalysisTest {

    private final NewsCategoryDetector categoryDetector = new NewsCategoryDetector();
    private final SectorImpactResolver sectorImpactResolver = new SectorImpactResolver();
    private final NewsImpactPromptBuilder promptBuilder = new NewsImpactPromptBuilder();
    private final AiResponsePostProcessor postProcessor =
            new AiResponsePostProcessor(new TurkishFinancialTextCleaner(), new AiDisclaimerCleaner());

    private NewsService newsService;
    private AiGatewayService aiGatewayService;
    private AiResponseCacheService cacheService;

    private NewsImpactAnalysisService service;

    @BeforeEach
    void setUp() {
        newsService = mock(NewsService.class);
        aiGatewayService = mock(AiGatewayService.class);
        cacheService = mock(AiResponseCacheService.class);

        service = new NewsImpactAnalysisService(
                newsService,
                categoryDetector,
                sectorImpactResolver,
                promptBuilder,
                aiGatewayService,
                cacheService,
                postProcessor,
                new ObjectMapper(),
                mock(AiResponseLogHelper.class)
        );
    }

    @Test
    void getNewsImpactAnalysis_usesCorrectCacheKey() {
        when(newsService.getNewsById(42L)).thenReturn(makeNewsDto(42L, "TCMB haberi", null));
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(NewsImpactResponse.class), any()))
                .thenReturn(new AiResponseCacheService.LookupResult<>(
                        new NewsImpactResponse(
                                "42",
                                "ozet",
                                "etki",
                                List.of(),
                                "NEUTRAL",
                                "LOW",
                                List.of(),
                                "groq",
                                false,
                                AiResponseMetadata.fromAiResponse(new AiResponse("{}", AiProviderType.GROQ, false, "llama3", 1L), "FULL")
                        ),
                        false
                ));

        service.getNewsImpactAnalysis(42L);

        verify(cacheService).getOrComputeWithDynamicTtlStatus(
                eq("ai:news-impact:42"),
                eq(NewsImpactResponse.class),
                any()
        );
    }

    @Test
    void serviceReturnsMetadataWhenLlmSucceeds() {
        String llmJson = """
                {
                  "summary": "Haberin piyasa etkisi sinirli ama izlenmeli.",
                  "marketImpact": "Piyasa etkisi karisik olabilir.",
                  "highlights": ["Kalem 1", "Kalem 2"]
                }
                """;

        when(newsService.getNewsById(1L)).thenReturn(makeNewsDto(1L, "Brent petrol yukseldi", null));
        when(aiGatewayService.generate(eq(AiTaskType.NEWS_IMPACT_ANALYSIS), anyString()))
                .thenReturn(Optional.of(new AiResponse(llmJson, AiProviderType.GROQ, false, "llama3", 400L)));
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(NewsImpactResponse.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = (java.util.function.Supplier<?>) invocation.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return new AiResponseCacheService.LookupResult<>(cached.value(), false);
                });

        NewsImpactResponse result = service.getNewsImpactAnalysis(1L);

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().providerUsed()).isEqualTo("GROQ");
        assertThat(result.metadata().modelUsed()).isEqualTo("llama3");
        assertThat(result.metadata().aiEnhanced()).isTrue();
        assertThat(result.metadata().deterministicFallbackUsed()).isFalse();
        assertThat(result.metadata().cacheHit()).isFalse();
    }

    @Test
    void serviceUsesDeterministicFallback_whenAiReturnsEmpty() {
        when(newsService.getNewsById(7L)).thenReturn(makeNewsDto(7L, "TCMB karari", null));
        when(aiGatewayService.generate(any(), anyString())).thenReturn(Optional.empty());
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(NewsImpactResponse.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = (java.util.function.Supplier<?>) invocation.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return new AiResponseCacheService.LookupResult<>(cached.value(), false);
                });

        NewsImpactResponse result = service.getNewsImpactAnalysis(7L);

        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().deterministicFallbackUsed()).isTrue();
        assertThat(result.metadata().aiEnhanced()).isFalse();
        assertThat(result.sentiment()).isNotBlank();
        assertThat(result.affectedSectors()).isNotEmpty();
    }

    private NewsResponseDto makeNewsDto(Long id, String title, String summary) {
        return NewsResponseDto.builder()
                .id(id)
                .title(title)
                .summary(summary)
                .source("AA")
                .provider("AA")
                .regionScope("LOCAL")
                .publishedAt(LocalDateTime.now())
                .importanceScore(50)
                .build();
    }
}
