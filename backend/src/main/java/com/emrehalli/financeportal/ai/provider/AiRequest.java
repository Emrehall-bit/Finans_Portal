package com.emrehalli.financeportal.ai.provider;

public record AiRequest(
        AiTaskType taskType,
        String prompt,
        String cacheKey,
        Integer maxTokens,
        Double temperature
) {
    public static AiRequest of(AiTaskType taskType, String prompt) {
        return new AiRequest(taskType, prompt, null, null, null);
    }
}




