package com.emrehalli.financeportal.ai.provider;

import com.emrehalli.financeportal.ai.config.AiProperties;
import com.emrehalli.financeportal.ai.service.GeminiAiClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GeminiProvider implements AiProvider {

    private final GeminiAiClient client;
    private final AiProperties properties;

    public GeminiProvider(GeminiAiClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public AiProviderType getType() {
        return AiProviderType.GEMINI;
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return client.isConfigured();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        long start = System.currentTimeMillis();
        Optional<String> content = client.generate(request.prompt());
        if (content.isEmpty()) {
            throw new AiProviderException(AiProviderType.GEMINI, "No content returned from Gemini");
        }
        return new AiResponse(
                content.get(),
                AiProviderType.GEMINI,
                false,
                properties.getGemini().getModel(),
                System.currentTimeMillis() - start
        );
    }
}



