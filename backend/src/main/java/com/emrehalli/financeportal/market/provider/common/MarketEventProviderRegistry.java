package com.emrehalli.financeportal.market.provider.common;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class MarketEventProviderRegistry {

    private final List<MarketEventProvider> providers;

    public MarketEventProviderRegistry(List<MarketEventProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(MarketEventProvider::getProviderName))
                .toList();
    }

    public List<MarketEventProvider> getAllProviders() {
        return providers;
    }

    public List<MarketEventProvider> getActiveProviders() {
        return providers.stream()
                .filter(MarketEventProvider::isActive)
                .toList();
    }

    public Optional<MarketEventProvider> findByName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }

        return providers.stream()
                .filter(provider -> provider.getProviderName().equalsIgnoreCase(providerName))
                .findFirst();
    }

    public List<String> getActiveProviderNames() {
        return getActiveProviders().stream()
                .map(MarketEventProvider::getProviderName)
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();
    }
}
