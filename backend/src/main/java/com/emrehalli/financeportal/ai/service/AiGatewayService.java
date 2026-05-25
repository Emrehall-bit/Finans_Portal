package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.config.AiProperties;
import com.emrehalli.financeportal.ai.provider.AiRequest;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.routing.AiFallbackExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Single entry point for all AI generation requests.
 * Routes each task to the correct primary provider with automatic fallback.
 * Callers should NOT interact with GeminiAiClient or GroqAiClient directly.
 */
@Service
public class AiGatewayService {

    private static final Logger logger = LogManager.getLogger(AiGatewayService.class);

    private final AiFallbackExecutor fallbackExecutor;
    private final AiProperties properties;

    public AiGatewayService(AiFallbackExecutor fallbackExecutor, AiProperties properties) {
        this.fallbackExecutor = fallbackExecutor;
        this.properties = properties;
    }

    /**
     * Generates an AI response for the given task type and prompt.
     * Returns empty if AI is disabled or all providers fail.
     */
    public Optional<AiResponse> generate(AiTaskType taskType, String prompt) {
        if (!properties.isEnabled()) {
            logger.debug("AI disabled, skipping generation. task={}", taskType);
            return Optional.empty();
        }
        return fallbackExecutor.execute(AiRequest.of(taskType, prompt));
    }
}



