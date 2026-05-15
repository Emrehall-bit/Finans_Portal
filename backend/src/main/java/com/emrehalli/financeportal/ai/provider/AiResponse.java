package com.emrehalli.financeportal.ai.provider;

public record AiResponse(
        String content,
        AiProviderType provider,
        boolean fallbackUsed,
        String model,
        long durationMs
) {
    public AiResponse withFallback() {
        return new AiResponse(content, provider, true, model, durationMs);
    }
}
