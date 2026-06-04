package com.emrehalli.financeportal.ai.portfolio;

import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.postprocess.AiDisclaimerCleaner;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.postprocess.TurkishFinancialTextCleaner;
import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.entity.PortfolioVisibility;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import com.emrehalli.financeportal.portfolio.enums.SummaryStatus;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import com.emrehalli.financeportal.portfolio.service.PortfolioService;
import com.emrehalli.financeportal.portfolio.service.PortfolioValuationResult;
import com.emrehalli.financeportal.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioAnalysisServiceTest {

    private PortfolioService portfolioService;
    private PortfolioHoldingService portfolioHoldingService;
    private PortfolioAnalysisPromptBuilder promptBuilder;
    private AiGatewayService aiGatewayService;
    private AiResponseCacheService cacheService;

    private PortfolioAnalysisService service;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        portfolioHoldingService = mock(PortfolioHoldingService.class);
        promptBuilder = mock(PortfolioAnalysisPromptBuilder.class);
        aiGatewayService = mock(AiGatewayService.class);
        cacheService = mock(AiResponseCacheService.class);

        AiResponsePostProcessor postProcessor =
                new AiResponsePostProcessor(new TurkishFinancialTextCleaner(), new AiDisclaimerCleaner());

        service = new PortfolioAnalysisService(
                portfolioService,
                portfolioHoldingService,
                promptBuilder,
                aiGatewayService,
                cacheService,
                postProcessor,
                new ObjectMapper(),
                mock(AiResponseLogHelper.class)
        );
    }

    @Test
    void cacheKey_changesWhenPortfolioHoldingsChange() {
        Portfolio portfolio = portfolio(41L, "Dengeli Portfoy");
        when(portfolioService.getPortfolioEntityById(41L)).thenReturn(portfolio);
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(PortfolioAnalysisResponse.class), any()))
                .thenReturn(new AiResponseCacheService.LookupResult<>(fallbackResponse(41L, "Dengeli Portfoy"), false));

        when(portfolioHoldingService.getPortfolioValuation(41L))
                .thenReturn(new PortfolioValuationResult(List.of(
                        holding(10L, "THYAO", BigDecimal.TEN, BigDecimal.valueOf(280), BigDecimal.valueOf(305),
                                BigDecimal.valueOf(3050), BigDecimal.valueOf(250), BigDecimal.valueOf(8.9),
                                true, LocalDateTime.of(2026, 5, 17, 10, 0))
                ), summary(BigDecimal.valueOf(3050), BigDecimal.valueOf(250), BigDecimal.valueOf(8.9), 0)))
                .thenReturn(new PortfolioValuationResult(List.of(
                        holding(10L, "THYAO", BigDecimal.TEN, BigDecimal.valueOf(280), BigDecimal.valueOf(305),
                                BigDecimal.valueOf(3050), BigDecimal.valueOf(250), BigDecimal.valueOf(8.9),
                                true, LocalDateTime.of(2026, 5, 17, 10, 0)),
                        holding(11L, "PGSUS", BigDecimal.valueOf(5), BigDecimal.valueOf(220), BigDecimal.valueOf(210),
                                BigDecimal.valueOf(1050), BigDecimal.valueOf(-50), BigDecimal.valueOf(-4.5),
                                true, LocalDateTime.of(2026, 5, 17, 10, 5))
                ), summary(BigDecimal.valueOf(4100), BigDecimal.valueOf(200), BigDecimal.valueOf(5.1), 0)));

        service.getPortfolioAnalysis(41L);
        service.getPortfolioAnalysis(41L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheService, times(2))
                .getOrComputeWithDynamicTtlStatus(keyCaptor.capture(), eq(PortfolioAnalysisResponse.class), any());
        assertThat(keyCaptor.getAllValues()).hasSize(2);
        assertThat(keyCaptor.getAllValues().getFirst()).startsWith("ai:portfolio-analysis:41:");
        assertThat(keyCaptor.getAllValues().get(1)).startsWith("ai:portfolio-analysis:41:");
        assertThat(keyCaptor.getAllValues().getFirst()).isNotEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    void emptyPortfolio_returnsControlledFallbackWithoutAiCall() {
        stubPortfolioValuation(52L, "Bos Portfoy", List.of(), summary(null, null, null, 0));
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(PortfolioAnalysisResponse.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = (java.util.function.Supplier<?>) invocation.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return new AiResponseCacheService.LookupResult<>(cached.value(), false);
                });

        PortfolioAnalysisResponse result = service.getPortfolioAnalysis(52L);

        assertThat(result.metadata().deterministicFallbackUsed()).isTrue();
        assertThat(result.dataQuality()).isEqualTo(DataQuality.LIMITED);
        assertThat(result.summary()).contains("Portfoy bos");
        verify(aiGatewayService, never()).generate(eq(AiTaskType.PORTFOLIO_ANALYSIS), anyString());
    }

    @Test
    void geminiFallbackMetadata_isPreservedWhenAiReturnsResponse() {
        stubPortfolioValuation(88L, "Dengeli Portfoy", List.of(
                holding(1L, "THYAO", BigDecimal.valueOf(8), BigDecimal.valueOf(280), BigDecimal.valueOf(300),
                        BigDecimal.valueOf(2400), BigDecimal.valueOf(160), BigDecimal.valueOf(7.1), true,
                        LocalDateTime.of(2026, 5, 17, 9, 0)),
                holding(2L, "USDTRY", BigDecimal.valueOf(500), BigDecimal.valueOf(38), BigDecimal.valueOf(39),
                        BigDecimal.valueOf(19500), BigDecimal.valueOf(500), BigDecimal.valueOf(2.6), true,
                        LocalDateTime.of(2026, 5, 17, 9, 2))
        ), summary(BigDecimal.valueOf(21900), BigDecimal.valueOf(660), BigDecimal.valueOf(3.1), 0));
        when(cacheService.getOrComputeWithDynamicTtlStatus(anyString(), eq(PortfolioAnalysisResponse.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = (java.util.function.Supplier<?>) invocation.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return new AiResponseCacheService.LookupResult<>(cached.value(), false);
                });
        when(promptBuilder.build(any(), anyString())).thenReturn("prompt");
        when(aiGatewayService.generate(eq(AiTaskType.PORTFOLIO_ANALYSIS), anyString()))
                .thenReturn(Optional.of(new AiResponse("""
                        {
                          "summary": "Portfoy dagilimi iki ana eksende toplanmis durumda.",
                          "allocationComment": "Doviz agirligi savunmaci bir katman olusturuyor.",
                          "riskComment": "Tek varlik yogunlasmasi sinirli ancak iki tema etrafinda donuyor.",
                          "diversificationComment": "Cesitlilik temel seviyede ama genis degil.",
                          "strongestPositions": ["USDTRY gorece daha dayanikli."],
                          "weakestPositions": ["THYAO oynakligi daha yuksek olabilir."],
                          "riskSignals": ["Portfoy iki ana pozisyonda toplaniyor."],
                          "suggestions": ["Mevcut dagilim net okunabiliyor."],
                          "finalComment": "Portfoy dengeli ama iki ana eksenin davranisi sonucu belirliyor."
                        }
                        """, AiProviderType.GEMINI, true, "gemini-1.5", 380L)));

        PortfolioAnalysisResponse result = service.getPortfolioAnalysis(88L);

        assertThat(result.providerUsed()).isEqualTo("gemini");
        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().providerUsed()).isEqualTo("GEMINI");
        assertThat(result.metadata().fallbackUsed()).isTrue();
        assertThat(result.metadata().aiEnhanced()).isTrue();
    }

    private void stubPortfolioValuation(Long portfolioId,
                                        String portfolioName,
                                        List<PortfolioHoldingDto> holdings,
                                        PortfolioSummaryResponse summary) {
        when(portfolioService.getPortfolioEntityById(portfolioId)).thenReturn(portfolio(portfolioId, portfolioName));
        when(portfolioHoldingService.getPortfolioValuation(portfolioId))
                .thenReturn(new PortfolioValuationResult(holdings, summary));
    }

    private PortfolioAnalysisResponse fallbackResponse(Long portfolioId, String name) {
        return new PortfolioAnalysisResponse(
                portfolioId,
                name,
                null,
                null,
                null,
                "summary",
                "allocation",
                "risk",
                "diversification",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "final",
                DataQuality.LIMITED,
                null,
                false,
                AiResponseMetadata.deterministic(DataQuality.LIMITED.name())
        );
    }

    private Portfolio portfolio(Long id, String name) {
        return Portfolio.builder()
                .id(id)
                .portfolioName(name)
                .visibilityStatus(PortfolioVisibility.PRIVATE)
                .createdAt(LocalDateTime.of(2026, 5, 1, 12, 0))
                .user(User.builder().id(5L).keycloakId("owner-123").fullName("Owner").email("owner@example.com").build())
                .build();
    }

    private PortfolioHoldingDto holding(Long id,
                                        String symbol,
                                        BigDecimal quantity,
                                        BigDecimal buyPrice,
                                        BigDecimal currentPrice,
                                        BigDecimal currentValue,
                                        BigDecimal profitLoss,
                                        BigDecimal profitLossPercent,
                                        boolean valuationAvailable,
                                        LocalDateTime updatedAt) {
        return PortfolioHoldingDto.builder()
                .holdingId(id)
                .instrumentCode(symbol)
                .quantity(quantity)
                .buyPrice(buyPrice)
                .currentPrice(currentPrice)
                .currentValue(currentValue)
                .profitLoss(profitLoss)
                .profitLossPercent(profitLossPercent)
                .priceStatus(valuationAvailable ? PriceStatus.LIVE : PriceStatus.UNAVAILABLE)
                .valuationAvailable(valuationAvailable)
                .createdAt(updatedAt.minusDays(3))
                .updatedAt(updatedAt)
                .build();
    }

    private PortfolioSummaryResponse summary(BigDecimal currentValue,
                                             BigDecimal profitLoss,
                                             BigDecimal profitLossPercent,
                                             int missingPriceCount) {
        return new PortfolioSummaryResponse(
                BigDecimal.ZERO,
                currentValue,
                null,
                null,
                profitLoss,
                profitLossPercent,
                missingPriceCount == 0 ? SummaryStatus.COMPLETE : SummaryStatus.PARTIAL,
                missingPriceCount
        );
    }
}




