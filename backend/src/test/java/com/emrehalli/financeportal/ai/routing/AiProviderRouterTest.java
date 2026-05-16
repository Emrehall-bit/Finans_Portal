package com.emrehalli.financeportal.ai.routing;

import com.emrehalli.financeportal.ai.config.AiProperties;
import com.emrehalli.financeportal.ai.provider.AiProvider;
import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiProviderRouterTest {

    private AiProvider geminiProvider;
    private AiProvider groqProvider;
    private AiProviderRouter router;

    @BeforeEach
    void setUp() {
        geminiProvider = mock(AiProvider.class);
        when(geminiProvider.getType()).thenReturn(AiProviderType.GEMINI);

        groqProvider = mock(AiProvider.class);
        when(groqProvider.getType()).thenReturn(AiProviderType.GROQ);

        AiProperties properties = defaultProperties();
        router = new AiProviderRouter(List.of(geminiProvider, groqProvider), properties);
    }

    // ── Test 1: PAGE_ANALYSIS primary is GROQ ────────────────────

    @Test
    void pageAnalysis_primaryIsGroq() {
        AiProvider primary = router.getPrimary(AiTaskType.PAGE_ANALYSIS);
        assertThat(primary.getType()).isEqualTo(AiProviderType.GROQ);
    }

    @Test
    void pageAnalysis_fallbackIsGemini() {
        AiProvider fallback = router.getFallback(AiTaskType.PAGE_ANALYSIS);
        assertThat(fallback.getType()).isEqualTo(AiProviderType.GEMINI);
    }

    // ── Test 2: CHAT primary is GEMINI ───────────────────────────

    @Test
    void chat_primaryIsGemini() {
        AiProvider primary = router.getPrimary(AiTaskType.CHAT);
        assertThat(primary.getType()).isEqualTo(AiProviderType.GEMINI);
    }

    @Test
    void chat_fallbackIsGroq() {
        AiProvider fallback = router.getFallback(AiTaskType.CHAT);
        assertThat(fallback.getType()).isEqualTo(AiProviderType.GROQ);
    }

    // ── All GROQ-primary tasks ───────────────────────────────────

    @Test
    void technicalAnalysis_primaryIsGroq() {
        assertThat(router.getPrimary(AiTaskType.TECHNICAL_ANALYSIS).getType()).isEqualTo(AiProviderType.GROQ);
    }

    @Test
    void fundamentalAnalysis_primaryIsGroq() {
        assertThat(router.getPrimary(AiTaskType.FUNDAMENTAL_ANALYSIS).getType()).isEqualTo(AiProviderType.GROQ);
    }

    @Test
    void companyComparison_primaryIsGroq() {
        assertThat(router.getPrimary(AiTaskType.COMPANY_COMPARISON).getType()).isEqualTo(AiProviderType.GROQ);
    }

    @Test
    void companyComparison_fallbackIsGemini() {
        assertThat(router.getFallback(AiTaskType.COMPANY_COMPARISON).getType()).isEqualTo(AiProviderType.GEMINI);
    }

    @Test
    void newsSummary_primaryIsGroq() {
        assertThat(router.getPrimary(AiTaskType.NEWS_SUMMARY).getType()).isEqualTo(AiProviderType.GROQ);
    }

    // ── Routing can be overridden via config ─────────────────────

    @Test
    void routingConfig_overrideChangesProvider() {
        AiProperties overridden = new AiProperties();
        overridden.getRouting().getChat().setPrimary(AiProviderType.GROQ);

        AiProviderRouter overriddenRouter =
                new AiProviderRouter(List.of(geminiProvider, groqProvider), overridden);

        assertThat(overriddenRouter.getPrimary(AiTaskType.CHAT).getType()).isEqualTo(AiProviderType.GROQ);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private AiProperties defaultProperties() {
        return new AiProperties();
    }
}
