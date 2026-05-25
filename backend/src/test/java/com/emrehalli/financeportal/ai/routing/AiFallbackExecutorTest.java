package com.emrehalli.financeportal.ai.routing;

import com.emrehalli.financeportal.ai.provider.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiFallbackExecutorTest {

    private AiProvider primaryProvider;
    private AiProvider fallbackProvider;
    private AiProviderRouter router;
    private AiFallbackExecutor executor;

    private static final AiRequest TECHNICAL_REQUEST =
            AiRequest.of(AiTaskType.TECHNICAL_ANALYSIS, "test prompt");

    private static final AiResponse GROQ_RESPONSE = new AiResponse(
            "{\"signal\":\"POSITIVE\"}", AiProviderType.GROQ, false, "llama-3.3-70b-versatile", 200L);

    private static final AiResponse GEMINI_RESPONSE = new AiResponse(
            "{\"signal\":\"NEUTRAL\"}", AiProviderType.GEMINI, false, "gemini-2.5-flash", 300L);

    @BeforeEach
    void setUp() {
        primaryProvider = mock(AiProvider.class);
        when(primaryProvider.getType()).thenReturn(AiProviderType.GROQ);
        when(primaryProvider.isConfigured()).thenReturn(true);

        fallbackProvider = mock(AiProvider.class);
        when(fallbackProvider.getType()).thenReturn(AiProviderType.GEMINI);
        when(fallbackProvider.isConfigured()).thenReturn(true);

        router = mock(AiProviderRouter.class);
        when(router.getPrimary(any())).thenReturn(primaryProvider);
        when(router.getFallback(any())).thenReturn(fallbackProvider);

        executor = new AiFallbackExecutor(router);
    }

    // ── Test 3a: Primary success → fallback not called ───────────

    @Test
    void primarySuccess_fallbackNotCalled() {
        when(primaryProvider.generate(any())).thenReturn(GROQ_RESPONSE);

        Optional<AiResponse> result = executor.execute(TECHNICAL_REQUEST);

        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo(AiProviderType.GROQ);
        assertThat(result.get().fallbackUsed()).isFalse();
        verify(fallbackProvider, never()).generate(any());
    }

    // ── Test 3b: Primary fails → fallback is tried ───────────────

    @Test
    void primaryFails_fallbackIsCalled() {
        when(primaryProvider.generate(any()))
                .thenThrow(new AiProviderException(AiProviderType.GROQ, "429 rate limited"));
        when(fallbackProvider.generate(any())).thenReturn(GEMINI_RESPONSE);

        Optional<AiResponse> result = executor.execute(TECHNICAL_REQUEST);

        assertThat(result).isPresent();
        verify(fallbackProvider).generate(any());
    }

    // ── Fallback response has fallbackUsed=true ──────────────────

    @Test
    void fallbackResponse_hasCorrectMetadata() {
        when(primaryProvider.generate(any()))
                .thenThrow(new AiProviderException(AiProviderType.GROQ, "503 unavailable"));
        when(fallbackProvider.generate(any())).thenReturn(GEMINI_RESPONSE);

        Optional<AiResponse> result = executor.execute(TECHNICAL_REQUEST);

        assertThat(result).isPresent();
        assertThat(result.get().fallbackUsed()).isTrue();
        assertThat(result.get().provider()).isEqualTo(AiProviderType.GEMINI);
    }

    // ── Both providers fail → empty ──────────────────────────────

    @Test
    void bothProvidersFail_returnsEmpty() {
        when(primaryProvider.generate(any()))
                .thenThrow(new AiProviderException(AiProviderType.GROQ, "timeout"));
        when(fallbackProvider.generate(any()))
                .thenThrow(new AiProviderException(AiProviderType.GEMINI, "quota exceeded"));

        Optional<AiResponse> result = executor.execute(TECHNICAL_REQUEST);

        assertThat(result).isEmpty();
    }

    // ── Test 5: Empty/null content is not a cached value ─────────
    // The providers throw AiProviderException on empty content, so
    // AiFallbackExecutor never returns an AiResponse with null content.

    @Test
    void providerReturnsEmpty_treatedAsFail_fallbackTried() {
        when(primaryProvider.generate(any()))
                .thenThrow(new AiProviderException(AiProviderType.GROQ, "No content returned"));
        when(fallbackProvider.generate(any())).thenReturn(GEMINI_RESPONSE);

        Optional<AiResponse> result = executor.execute(TECHNICAL_REQUEST);

        assertThat(result).isPresent();
        assertThat(result.get().content()).isNotBlank();
        verify(fallbackProvider).generate(any());
    }

    // ── Primary not configured → skip to fallback ────────────────

    @Test
    void primaryNotConfigured_fallbackDirectlyUsed() {
        when(primaryProvider.isConfigured()).thenReturn(false);
        when(fallbackProvider.generate(any())).thenReturn(GEMINI_RESPONSE);

        Optional<AiResponse> result = executor.execute(TECHNICAL_REQUEST);

        assertThat(result).isPresent();
        verify(primaryProvider, never()).generate(any());
        verify(fallbackProvider).generate(any());
    }

    // ── Fallback same as primary → no retry ─────────────────────

    @Test
    void fallbackSameAsPrimary_noRetry() {
        when(fallbackProvider.getType()).thenReturn(AiProviderType.GROQ);
        when(primaryProvider.generate(any()))
                .thenThrow(new AiProviderException(AiProviderType.GROQ, "failed"));

        Optional<AiResponse> result = executor.execute(TECHNICAL_REQUEST);

        assertThat(result).isEmpty();
        verify(fallbackProvider, never()).generate(any());
    }
}



