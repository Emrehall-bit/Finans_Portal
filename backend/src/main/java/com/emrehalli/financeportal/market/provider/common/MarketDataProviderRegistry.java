package com.emrehalli.financeportal.market.provider.common;

import com.emrehalli.financeportal.market.enums.InstrumentType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class MarketDataProviderRegistry {

    private final List<MarketDataProvider> providers;

    public MarketDataProviderRegistry(List<MarketDataProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(MarketDataProvider::getProviderName))
                .toList();
    }

    public List<MarketDataProvider> getAllProviders() {
        return providers;
    }

    public List<MarketDataProvider> getActiveProviders() {
        return providers.stream()
                .filter(MarketDataProvider::isActive)
                .toList();
    }

    public Optional<MarketDataProvider> findByName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }

        return providers.stream()
                .filter(provider -> provider.getProviderName().equalsIgnoreCase(providerName))
                .findFirst();
    }

    public List<MarketDataProvider> findByInstrumentType(InstrumentType instrumentType) {
        if (instrumentType == null) {
            return List.of();
        }

        return providers.stream()
                .filter(provider -> provider.getSupportedInstrumentTypes().contains(instrumentType))
                .toList();
    }

    public List<String> getActiveProviderNames() {
        return getActiveProviders().stream()
                .map(MarketDataProvider::getProviderName)
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();
    }
}
