package com.emrehalli.financeportal.ai.unified;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.AiSignal;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.RiskLevel;
import com.emrehalli.financeportal.ai.postprocess.AiDisclaimerCleaner;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.postprocess.TurkishFinancialTextCleaner;
import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiFundamentalAnalysisService;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiTechnicalAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    // ── Real instances (pure logic, no Spring needed) ─────────────
    private final UnifiedInsightAssembler      assembler     = new UnifiedInsightAssembler();
    private final UnifiedAnalysisPromptBuilder promptBuilder = new UnifiedAnalysisPromptBuilder();
    private final AiResponsePostProcessor      postProcessor =
            new AiResponsePostProcessor(new TurkishFinancialTextCleaner(), new AiDisclaimerCleaner());

    // ── Mocked external dependencies ──────────────────────────────
    private AiTechnicalAnalysisService  technicalService;
    private AiFundamentalAnalysisService fundamentalService;
    private AiGatewayService            aiGatewayService;
    private AiResponseCacheService      cacheService;

    private UnifiedAiAnalysisService service;

    @BeforeEach
    void setUp() {
        technicalService   = mock(AiTechnicalAnalysisService.class);
        fundamentalService = mock(AiFundamentalAnalysisService.class);
        aiGatewayService   = mock(AiGatewayService.class);
        cacheService       = mock(AiResponseCacheService.class);

        service = new UnifiedAiAnalysisService(
                technicalService, fundamentalService,
                assembler, promptBuilder,
                aiGatewayService, cacheService,
                postProcessor, new ObjectMapper()
        );
    }

    // ── 1. Context assembly — tech only (CRYPTO) ──────────────────

    @Test
    void assemble_cryptoHasNoFundamentals() {
        AiTechnicalAnalysisResponse tech = makeTech(AiSignal.POSITIVE);
        UnifiedAnalysisContext ctx = assembler.assemble("BTCUSDT", tech, null);

        assertThat(ctx.hasFundamentals()).isFalse();
        assertThat(ctx.strengths()).isEmpty();
        assertThat(ctx.fundamentalRisks()).isEmpty();
        assertThat(ctx.technicalSignal()).isEqualTo("Pozitif");
    }

    // ── 2. Context assembly — tech + fundamental (STOCK) ─────────

    @Test
    void assemble_stockHasBothSides() {
        AiTechnicalAnalysisResponse  tech  = makeTech(AiSignal.NEGATIVE);
        AiFundamentalAnalysisResponse fund = makeFund(FinancialHealth.STRONG, List.of("ROE güçlü"), List.of("Borç yüksek"), List.of("Likidite riski"));

        UnifiedAnalysisContext ctx = assembler.assemble("THYAO", tech, fund);

        assertThat(ctx.hasFundamentals()).isTrue();
        assertThat(ctx.strengths()).contains("ROE güçlü");
        assertThat(ctx.weaknesses()).contains("Borç yüksek");
        assertThat(ctx.fundamentalRisks()).contains("Likidite riski");
        assertThat(ctx.financialHealthLabel()).isEqualTo("Güçlü");
    }

    // ── 3. Conflict notes ─────────────────────────────────────────

    @Test
    void conflictNote_techPositiveFundRisky() {
        AiTechnicalAnalysisResponse  tech  = makeTech(AiSignal.POSITIVE);
        AiFundamentalAnalysisResponse fund = makeFund(FinancialHealth.RISKY, List.of(), List.of(), List.of());

        String note = assembler.buildConflictNote(tech, fund);
        assertThat(note).contains("Kısa vadeli momentum olumlu olsa da");
    }

    @Test
    void conflictNote_techNegativeFundStrong() {
        AiTechnicalAnalysisResponse  tech  = makeTech(AiSignal.NEGATIVE);
        AiFundamentalAnalysisResponse fund = makeFund(FinancialHealth.STABLE, List.of(), List.of(), List.of());

        String note = assembler.buildConflictNote(tech, fund);
        assertThat(note).contains("Temel görünüm olumlu");
        assertThat(note).contains("Uzun vadeli görünüm");
    }

    @Test
    void conflictNote_bothPositive() {
        AiTechnicalAnalysisResponse  tech  = makeTech(AiSignal.POSITIVE);
        AiFundamentalAnalysisResponse fund = makeFund(FinancialHealth.STRONG, List.of(), List.of(), List.of());

        String note = assembler.buildConflictNote(tech, fund);
        assertThat(note).contains("birbirini destekler");
    }

    @Test
    void conflictNote_bothNegative() {
        AiTechnicalAnalysisResponse  tech  = makeTech(AiSignal.RISKY);
        AiFundamentalAnalysisResponse fund = makeFund(FinancialHealth.RISKY, List.of(), List.of(), List.of());

        String note = assembler.buildConflictNote(tech, fund);
        assertThat(note).contains("olumsuz işaretler");
    }

    @Test
    void conflictNote_noFundamentals() {
        AiTechnicalAnalysisResponse tech = makeTech(AiSignal.NEUTRAL);
        String note = assembler.buildConflictNote(tech, null);
        assertThat(note).contains("Temel analiz bu enstrüman tipi için geçerli değil");
    }

    // ── 4. Hallucination-safe prompt ──────────────────────────────

    @Test
    void prompt_containsAntiHallucinationRules() {
        UnifiedAnalysisContext ctx = assembler.assemble("TEST", makeTech(AiSignal.NEUTRAL), null);
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("Ham sayı veya yüzde listeleme yapma");
        assertThat(prompt).contains("Kesin fiyat hedefi verme");
        assertThat(prompt).contains("al', 'sat', 'tut");
        assertThat(prompt).contains("uydurma");
    }

    @Test
    void prompt_containsControlledLanguageInstruction() {
        UnifiedAnalysisContext ctx = assembler.assemble("TEST", makeTech(AiSignal.POSITIVE), null);
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("görünüyor");
        assertThat(prompt).contains("mevcut verilere göre");
    }

    @Test
    void prompt_containsSymbolAndTechnicalData() {
        AiTechnicalAnalysisResponse tech = makeTech(AiSignal.POSITIVE);
        UnifiedAnalysisContext ctx = assembler.assemble("THYAO", tech, null);
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("THYAO");
        assertThat(prompt).contains("TEKNİK GÖRÜNÜM");
    }

    @Test
    void prompt_fundamentalBlockPresentWhenHasFundamentals() {
        AiTechnicalAnalysisResponse  tech  = makeTech(AiSignal.NEUTRAL);
        AiFundamentalAnalysisResponse fund = makeFund(FinancialHealth.STABLE, List.of("Güçlü taraf"), List.of(), List.of());
        UnifiedAnalysisContext ctx = assembler.assemble("THYAO", tech, fund);
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).contains("TEMEL GÖRÜNÜM");
        assertThat(prompt).contains("Güçlü taraf");
    }

    @Test
    void prompt_fundamentalBlockAbsentForCrypto() {
        UnifiedAnalysisContext ctx = assembler.assemble("BTC", makeTech(AiSignal.POSITIVE), null);
        String prompt = promptBuilder.build(ctx);
        assertThat(prompt).doesNotContain("TEMEL GÖRÜNÜM");
    }

    // ── 5. Cache key ──────────────────────────────────────────────

    @Test
    void getUnifiedAnalysis_usesCorrectCacheKey() {
        when(cacheService.getOrComputeWithDynamicTtl(anyString(), eq(UnifiedAnalysisResponse.class), any()))
                .thenReturn(new UnifiedAnalysisResponse("THYAO", "Summary", List.of(), List.of(), "groq", false));

        service.getUnifiedAnalysis("thyao", "STOCK");

        verify(cacheService).getOrComputeWithDynamicTtl(
                eq("ai:unified:THYAO"),
                eq(UnifiedAnalysisResponse.class),
                any()
        );
    }

    @Test
    void getUnifiedAnalysis_normalizesSymbolToUpperCase() {
        when(cacheService.getOrComputeWithDynamicTtl(anyString(), eq(UnifiedAnalysisResponse.class), any()))
                .thenReturn(new UnifiedAnalysisResponse("FROTO", "Summary", List.of(), List.of(), "groq", false));

        service.getUnifiedAnalysis("froto", "STOCK");

        verify(cacheService).getOrComputeWithDynamicTtl(
                eq("ai:unified:FROTO"),
                eq(UnifiedAnalysisResponse.class),
                any()
        );
    }

    // ── 6. Endpoint response format ───────────────────────────────

    @Test
    void serviceReturnsCorrectResponseShape() {
        String llmJson = "{\"summary\": \"Test özeti.\", " +
                "\"highlights\": [\"öne çıkan 1\", \"öne çıkan 2\"], " +
                "\"risks\": [\"risk 1\"]}";

        when(technicalService.getTechnicalComment("THYAO")).thenReturn(makeTech(AiSignal.POSITIVE));
        when(fundamentalService.getFundamentalComment("THYAO"))
                .thenReturn(makeFund(FinancialHealth.STABLE, List.of(), List.of(), List.of()));
        when(aiGatewayService.generate(eq(AiTaskType.PAGE_ANALYSIS), anyString()))
                .thenReturn(Optional.of(new AiResponse(llmJson, AiProviderType.GROQ, false, "llama3", 500)));
        when(cacheService.getOrComputeWithDynamicTtl(anyString(), eq(UnifiedAnalysisResponse.class), any()))
                .thenAnswer(inv -> {
                    var supplier = (java.util.function.Supplier<?>) inv.getArgument(2);
                    var cached = (AiResponseCacheService.CachedValue<?>) supplier.get();
                    return cached.value();
                });

        UnifiedAnalysisResponse result = service.getUnifiedAnalysis("THYAO", "STOCK");

        assertThat(result.symbol()).isEqualTo("THYAO");
        assertThat(result.summary()).isNotBlank();
        assertThat(result.highlights()).isNotNull();
        assertThat(result.risks()).isNotNull();
        assertThat(result.provider()).isNotBlank();
    }

    // ── Fixture helpers ───────────────────────────────────────────

    private AiTechnicalAnalysisResponse makeTech(AiSignal signal) {
        return new AiTechnicalAnalysisResponse(
                "TEST",
                "Teknik özet metni.",
                "Trend yorumu.",
                "Momentum yorumu.",
                RiskLevel.MEDIUM,
                signal,
                "Bu yorum yatırım tavsiyesi değildir."
        );
    }

    private AiFundamentalAnalysisResponse makeFund(FinancialHealth health,
                                                    List<String> strengths,
                                                    List<String> weaknesses,
                                                    List<String> risks) {
        return new AiFundamentalAnalysisResponse(
                "TEST",
                "Finansal özet.",
                strengths,
                weaknesses,
                risks,
                "Büyüme yorumu.",
                health,
                "Bu yorum yatırım tavsiyesi değildir."
        );
    }
}
