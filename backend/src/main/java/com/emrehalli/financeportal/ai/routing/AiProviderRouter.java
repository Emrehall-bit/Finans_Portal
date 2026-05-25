package com.emrehalli.financeportal.ai.routing;

import com.emrehalli.financeportal.ai.config.AiProperties;
import com.emrehalli.financeportal.ai.provider.AiProvider;
import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AiProviderRouter {

    private final Map<AiProviderType, AiProvider> providerMap;
    private final AiProperties properties;

    public AiProviderRouter(List<AiProvider> providers, AiProperties properties) {
        this.providerMap = new EnumMap<>(AiProviderType.class);
        providers.forEach(p -> providerMap.put(p.getType(), p));
        this.properties = properties;
    }

    public AiProvider getPrimary(AiTaskType taskType) {
        AiProviderType type = properties.getRouting().getForTask(taskType).getPrimary();
        return providerMap.get(type);
    }

    public AiProvider getFallback(AiTaskType taskType) {
        AiProviderType type = properties.getRouting().getForTask(taskType).getFallback();
        return providerMap.get(type);
    }
}



