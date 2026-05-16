package com.emrehalli.financeportal.ai.news;

import com.emrehalli.financeportal.ai.postprocess.AiDisclaimerCleaner;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.postprocess.TurkishFinancialTextCleaner;
import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
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

    // Real instances — pure logic, no Spring
    private final NewsCategoryDetector    categoryDetector     = new NewsCategoryDetector();
    private final SectorImpactResolver    sectorImpactResolver = new SectorImpactResolver();
    private final NewsImpactPromptBuilder promptBuilder        = new NewsImpactPromptBuilder();
    private final AiResponsePostProcessor postProcessor        =
            new AiResponsePostProcessor(new TurkishFinancialTextCleaner(), new AiDisclaimerCleaner());

    // Mocks
    private NewsService          newsService;
    private AiGatewayService     aiGatewayService;
    private AiResponseCacheService cacheService;

    private NewsImpactAnalysisService service;

    @BeforeEach
    void setUp() {
        newsService      = mock(NewsService.class);
        aiGatewayService = mock(AiGatewayService.class);
        cacheService     = mock(AiResponseCacheService.class);

        service = new NewsImpactAnalysisService(
                newsService, categoryDetector, sectorImpactResolver,
                promptBuilder, aiGatewayService, cacheService,
                postProcessor, new ObjectMapper()
        );
    }

    // ── 1. Category Detection ─────────────────────────────────────

    @Test
    void detectCategory_tcmb() {
        assertThat(categoryDetector.detect("TCMB faiz kararını açıkladı", null, null))
                .isEqualTo(NewsCategory.TCMB);
    }

    @Test
    void detectCategory_inflation() {
        assertThat(categoryDetector.detect("Türkiye enflasyonu yüzde 60'a dayandı", null, null))
                .isEqualTo(NewsCategory.INFLATION);
    }

    @Test
    void detectCategory_fed() {
        assertThat(categoryDetector.detect("Federal Reserve faiz kararı açıklandı", null, null))
                .isEqualTo(NewsCategory.FED);
    }

    @Test
    void detectCategory_crypto() {
        assertThat(categoryDetector.detect("Bitcoin fiyatı rekor kırdı", "kripto piyasaları hareketlendi", null))
                .isEqualTo(NewsCategory.CRYPTO);
    }

    @Test
    void detectCategory_defense() {
        assertThat(categoryDetector.detect("Savunma sanayii ihracat sözleşmesi imzalandı", null, null))
                .isEqualTo(NewsCategory.DEFENSE);
    }

    @Test
    void detectCategory_earnings() {
        assertThat(categoryDetector.detect("THYAO bilanço açıkladı, net kâr beklentinin üzerinde", null, null))
                .isEqualTo(NewsCategory.EARNINGS);
    }

    @Test
    void detectCategory_dividend() {
        assertThat(categoryDetector.detect("Şirket temettü dağıtım kararı aldı", null, null))
                .isEqualTo(NewsCategory.DIVIDEND);
    }

    @Test
    void detectCategory_fallsBackToGeneral() {
        assertThat(categoryDetector.detect("Hava durumu raporu", "Bugün güneşli olacak", null))
                .isEqualTo(NewsCategory.GENERAL);
    }

    // ── 2. Sector Resolver ────────────────────────────────────────

    @Test
    void resolveOilEnergy_mixedSentimentWithAirlinesAndEnergy() {
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(NewsCategory.OIL_ENERGY, "Petrol fiyatları yükseldi", null);
        assertThat(impact.sentiment()).isEqualTo("MIXED");
        assertThat(impact.sectors()).anyMatch(s -> s.contains("HAVAYOLU"));
        assertThat(impact.sectors()).anyMatch(s -> s.contains("ENERJİ"));
    }

    @Test
    void resolveInterestRateHike_mixedWithBanksAndGrowthStocks() {
        SectorImpactResolver.SectorImpact impact = sectorImpactResolver.resolve(
                NewsCategory.INTEREST_RATE, "Faiz oranı artırdı", "Merkez bankası faiz artışı kararı");
        assertThat(impact.sentiment()).isEqualTo("MIXED");
        assertThat(impact.sectors()).anyMatch(s -> s.contains("BANKALAR"));
        assertThat(impact.sectors()).anyMatch(s -> s.contains("BÜYÜME"));
    }

    @Test
    void resolveInterestRateCut_positiveWithLowRisk() {
        SectorImpactResolver.SectorImpact impact = sectorImpactResolver.resolve(
                NewsCategory.INTEREST_RATE, "Faiz indirimi açıklandı", "Faiz düşüş kararı alındı");
        assertThat(impact.sentiment()).isEqualTo("POSITIVE");
        assertThat(impact.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void resolveInflation_negativeHighRisk() {
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(NewsCategory.INFLATION, "Enflasyon yükseldi", null);
        assertThat(impact.sentiment()).isEqualTo("NEGATIVE");
        assertThat(impact.riskLevel()).isEqualTo("HIGH");
        assertThat(impact.sectors()).anyMatch(s -> s.contains("MALİYET"));
    }

    @Test
    void resolveDefense_positiveWithDefenseSector() {
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(NewsCategory.DEFENSE, "Savunma sözleşmesi imzalandı", null);
        assertThat(impact.sentiment()).isEqualTo("POSITIVE");
        assertThat(impact.sectors()).anyMatch(s -> s.contains("SAVUNMA"));
    }

    @Test
    void resolveCrypto_highRisk() {
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(NewsCategory.CRYPTO, "Bitcoin çöktü", null);
        assertThat(impact.riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void resolveTcmbCut_positiveRiskAppetite() {
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(NewsCategory.TCMB, "TCMB faiz indirdi", "Faiz indirimi kararı alındı");
        assertThat(impact.sentiment()).isEqualTo("POSITIVE");
        assertThat(impact.sectors()).anyMatch(s -> s.contains("RİSK İŞTAHI"));
    }

    @Test
    void resolveDividend_positiveWithLowRisk() {
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(NewsCategory.DIVIDEND, "Şirket temettü dağıtacak", null);
        assertThat(impact.sentiment()).isEqualTo("POSITIVE");
        assertThat(impact.riskLevel()).isEqualTo("LOW");
    }

    // ── 3. Prompt Safety ─────────────────────────────────────────

    @Test
    void prompt_containsAntiHallucinationRules() {
        NewsImpactContext ctx = makeContext(NewsCategory.OIL_ENERGY, "MIXED", "MEDIUM");
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("uydurma");
        assertThat(prompt).contains("etkileyebilir");
        assertThat(prompt).contains("Kesin fiyat hareketi");
        assertThat(prompt).contains("spekülatif tahmin");
    }

    @Test
    void prompt_containsNewsTitleAndCategory() {
        NewsImpactContext ctx = makeContext(NewsCategory.TCMB, "NEUTRAL", "MEDIUM");
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("TCMB test haberi başlığı");
        assertThat(prompt).contains("TCMB");
    }

    @Test
    void prompt_containsSectorImpacts() {
        NewsImpactContext ctx = makeContext(NewsCategory.OIL_ENERGY, "MIXED", "MEDIUM");
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("HAVAYOLU");
        assertThat(prompt).contains("ENERJİ");
    }

    @Test
    void prompt_containsJsonOutputInstruction() {
        NewsImpactContext ctx = makeContext(NewsCategory.GENERAL, "NEUTRAL", "LOW");
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("\"summary\"");
        assertThat(prompt).contains("\"marketImpact\"");
        assertThat(prompt).contains("\"highlights\"");
    }

    // ── 4. Cache Key ──────────────────────────────────────────────

    @Test
    void getNewsImpactAnalysis_usesCorrectCacheKey() {
        when(newsService.getNewsById(42L)).thenReturn(makeNewsDto(42L, "TCMB haberi", null));
        when(cacheService.getOrComputeWithDynamicTtl(anyString(), eq(NewsImpactResponse.class), any()))
                .thenReturn(new NewsImpactResponse("42", "özet", "etki", List.of(), "NEUTRAL", "LOW", List.of(), "groq", false));

        service.getNewsImpactAnalysis(42L);

        verify(cacheService).getOrComputeWithDynamicTtl(
                eq("ai:news-impact:42"),
                eq(NewsImpactResponse.class),
                any()
        );
    }

    // ── 5. Endpoint Response Shape ────────────────────────────────

    @Test
    void serviceReturnsCorrectResponseShape() {
        String llmJson = "{\"summary\": \"Haberin finansal etkisi olabilir.\", " +
                "\"marketImpact\": \"Piyasa olumsuz etkilenebilir.\", " +
                "\"highlights\": [\"öne çıkan 1\", \"öne çıkan 2\"]}";

        when(newsService.getNewsById(1L))
                .thenReturn(makeNewsDto(1L, "Brent petrol fiyatları yükseldi", null));
        when(aiGatewayService.generate(eq(AiTaskType.NEWS_IMPACT_ANALYSIS), anyString()))
                .thenReturn(Optional.of(new AiResponse(llmJson, AiProviderType.GROQ, false, "llama3", 400)));
        when(cacheService.getOrComputeWithDynamicTtl(anyString(), eq(NewsImpactResponse.class), any()))
                .thenAnswer(inv -> {
                    var supplier = (java.util.function.Supplier<?>) inv.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return cached.value();
                });

        NewsImpactResponse result = service.getNewsImpactAnalysis(1L);

        assertThat(result.newsId()).isEqualTo("1");
        assertThat(result.summary()).isNotBlank();
        assertThat(result.marketImpact()).isNotBlank();
        assertThat(result.affectedSectors()).isNotNull();
        assertThat(result.sentiment()).isNotBlank();
        assertThat(result.riskLevel()).isNotBlank();
        assertThat(result.highlights()).isNotNull();
        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void serviceUsesDeterministicFallback_whenAiReturnsEmpty() {
        when(newsService.getNewsById(7L))
                .thenReturn(makeNewsDto(7L, "TCMB kararı açıklandı", null));
        when(aiGatewayService.generate(any(), anyString())).thenReturn(Optional.empty());
        when(cacheService.getOrComputeWithDynamicTtl(anyString(), eq(NewsImpactResponse.class), any()))
                .thenAnswer(inv -> {
                    var supplier = (java.util.function.Supplier<?>) inv.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return cached.value();
                });

        NewsImpactResponse result = service.getNewsImpactAnalysis(7L);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.provider()).isEqualTo("rule-based");
        assertThat(result.sentiment()).isNotBlank();
        assertThat(result.affectedSectors()).isNotEmpty();
    }

    // ── Fixtures ─────────────────────────────────────────────────

    private NewsImpactContext makeContext(NewsCategory category, String sentiment, String riskLevel) {
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(category, "TCMB test haberi başlığı", null);
        return new NewsImpactContext(
                1L, "TCMB test haberi başlığı", null, "AA", null,
                category, impact.sectors(), sentiment, riskLevel
        );
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
