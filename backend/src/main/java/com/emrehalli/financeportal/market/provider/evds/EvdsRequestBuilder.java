package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EvdsRequestBuilder {

    private final EvdsSeriesValidator validator;

    public EvdsRequestBuilder(EvdsSeriesValidator validator) {
        this.validator = validator;
    }

    public BuildResult build(List<EvdsProperties.SeriesConfig> seriesConfigs) {
        if (seriesConfigs == null || seriesConfigs.isEmpty()) {
            return new BuildResult(List.of(), List.of(), 0, 0);
        }

        List<EvdsSeriesRequest> validRequests = new ArrayList<>();
        List<String> invalidSeriesCodes = new ArrayList<>();

        for (EvdsProperties.SeriesConfig seriesConfig : seriesConfigs) {
            String providerSymbol = resolveProviderSymbol(seriesConfig);
            EvdsSeriesValidator.ValidationResult validation = validator.validate(providerSymbol);
            if (!validation.valid()) {
                invalidSeriesCodes.add(providerSymbol == null ? "<null>" : providerSymbol);
                continue;
            }

            validRequests.add(new EvdsSeriesRequest(
                    seriesConfig,
                    validation.originalCode(),
                    validation.normalizedCode()
            ));
        }

        int normalizedSeriesCount = validRequests.stream()
                .map(EvdsSeriesRequest::requestSeriesCode)
                .distinct()
                .mapToInt(ignored -> 1)
                .sum();

        return new BuildResult(List.copyOf(validRequests), List.copyOf(invalidSeriesCodes), seriesConfigs.size(), normalizedSeriesCount);
    }

    private String resolveProviderSymbol(EvdsProperties.SeriesConfig seriesConfig) {
        if (seriesConfig == null) {
            return null;
        }
        if (seriesConfig.getApiCode() != null && !seriesConfig.getApiCode().isBlank()) {
            return seriesConfig.getApiCode();
        }
        return seriesConfig.getEvdsKey();
    }

    public record EvdsSeriesRequest(
            EvdsProperties.SeriesConfig seriesConfig,
            String originalSeriesCode,
            String requestSeriesCode
    ) {
    }

    public record BuildResult(
            List<EvdsSeriesRequest> validRequests,
            List<String> invalidSeriesCodes,
            int requestedSeriesCount,
            int normalizedSeriesCount
    ) {
    }
}
