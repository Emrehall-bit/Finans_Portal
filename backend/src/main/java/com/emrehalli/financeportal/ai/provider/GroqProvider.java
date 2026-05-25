package com.emrehalli.financeportal.ai.provider;

import com.emrehalli.financeportal.ai.config.AiProperties;
import com.emrehalli.financeportal.ai.service.GroqAiClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GroqProvider implements AiProvider {

    private final GroqAiClient client;
    private final AiProperties properties;

    public GroqProvider(GroqAiClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public AiProviderType getType() {
        return AiProviderType.GROQ;
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
            throw new AiProviderException(AiProviderType.GROQ, "No content returned from Groq");
        }
        return new AiResponse(
                content.get(),
                AiProviderType.GROQ,
                false,
                properties.getGroq().getModel(),
                System.currentTimeMillis() - start
        );
    }
}



