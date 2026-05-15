package com.emrehalli.financeportal.ai.routing;

import com.emrehalli.financeportal.ai.provider.AiProvider;
import com.emrehalli.financeportal.ai.provider.AiRequest;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AiFallbackExecutor {

    private static final Logger logger = LogManager.getLogger(AiFallbackExecutor.class);

    private final AiProviderRouter router;

    public AiFallbackExecutor(AiProviderRouter router) {
        this.router = router;
    }

    public Optional<AiResponse> execute(AiRequest request) {
        AiProvider primary = router.getPrimary(request.taskType());
        AiProvider fallback = router.getFallback(request.taskType());

        // ── Primary attempt ───────────────────────────────────────
        if (primary != null && primary.isConfigured()) {
            try {
                AiResponse response = primary.generate(request);
                logger.info("AI primary success. task={}, provider={}, fallback=false, model={}, durationMs={}",
                        request.taskType(), primary.getType(), response.model(), response.durationMs());
                return Optional.of(response);
            } catch (Exception e) {
                logger.warn("AI primary failed. task={}, provider={}, reason={}. Attempting fallback.",
                        request.taskType(), primary.getType(), e.getMessage());
            }
        } else {
            logger.warn("AI primary not available. task={}, provider={}",
                    request.taskType(), primary == null ? "null" : primary.getType());
        }

        // ── Fallback attempt ──────────────────────────────────────
        if (fallback == null || !fallback.isConfigured()) {
            logger.error("AI fallback not available. task={}", request.taskType());
            return Optional.empty();
        }
        if (primary != null && fallback.getType() == primary.getType()) {
            logger.error("AI fallback is same as primary, skipping. task={}, provider={}", request.taskType(), fallback.getType());
            return Optional.empty();
        }

        try {
            AiResponse response = fallback.generate(request);
            AiResponse withFallback = response.withFallback();
            logger.info("AI fallback success. task={}, provider={}, fallback=true, model={}, durationMs={}",
                    request.taskType(), fallback.getType(), withFallback.model(), withFallback.durationMs());
            return Optional.of(withFallback);
        } catch (Exception e) {
            logger.error("AI fallback also failed. task={}, provider={}, reason={}",
                    request.taskType(), fallback.getType(), e.getMessage());
            return Optional.empty();
        }
    }
}
