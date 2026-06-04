package com.emrehalli.financeportal.ai.core.provider;

public interface AiProvider {
    AiProviderType getType();
    AiResponse generate(AiRequest request);
    boolean supports(AiTaskType taskType);
    boolean isConfigured();
}




