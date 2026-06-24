package com.emrehalli.financeportal.ai.core.dto;

import com.emrehalli.financeportal.ai.core.provider.AiResponse;

import java.time.LocalDateTime;

public record AiResponseMetadata(
        String providerUsed,
        String modelUsed,
        boolean fallbackUsed,
        boolean aiEnhanced,
        boolean deterministicFallbackUsed,
        boolean cacheHit,
        LocalDateTime generatedAt,
        String dataQuality
) {

    public static AiResponseMetadata fromAiResponse(AiResponse response, String dataQuality) {
        return new AiResponseMetadata(
                response.provider().name(),
                response.model(),
                response.fallbackUsed(),
                true,
                false,
                false,
                LocalDateTime.now(),
                dataQuality
        );
    }

    public static AiResponseMetadata deterministic(String dataQuality) {
        return new AiResponseMetadata(
                null,
                null,
                false,
                false,
                true,
                false,
                LocalDateTime.now(),
                dataQuality
        );
    }

    public AiResponseMetadata withCacheHit(boolean cacheHit) {
        return new AiResponseMetadata(
                providerUsed,
                modelUsed,
                fallbackUsed,
                aiEnhanced,
                deterministicFallbackUsed,
                cacheHit,
                generatedAt != null ? generatedAt : LocalDateTime.now(),
                dataQuality
        );
    }

    public AiResponseMetadata withDataQuality(String dataQuality) {
        return new AiResponseMetadata(
                providerUsed,
                modelUsed,
                fallbackUsed,
                aiEnhanced,
                deterministicFallbackUsed,
                cacheHit,
                generatedAt != null ? generatedAt : LocalDateTime.now(),
                dataQuality
        );
    }
}

