package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketProviderMappingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketProviderHealthService {

    private final ProviderCircuitBreakerService providerCircuitBreakerService;
    private final MarketProviderMappingRepository marketProviderMappingRepository;

    public MarketProviderHealthService(ProviderCircuitBreakerService providerCircuitBreakerService,
                                       MarketProviderMappingRepository marketProviderMappingRepository) {
        this.providerCircuitBreakerService = providerCircuitBreakerService;
        this.marketProviderMappingRepository = marketProviderMappingRepository;
    }

    public List<ProviderHealthSnapshot> getProviderHealth() {
        Map<DataSource, ProviderHealthAccumulator> accumulators = new EnumMap<>(DataSource.class);
        marketProviderMappingRepository.findByEnabledTrueAndInstrument_EnabledTrueOrderBySourceAscPriorityAscIdAsc()
                .forEach(mapping -> accumulators.computeIfAbsent(mapping.getSource(), ProviderHealthAccumulator::new).accept(mapping));

        return providerCircuitBreakerService.sources().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(source -> accumulators.getOrDefault(source, new ProviderHealthAccumulator(source)).snapshot(
                        providerCircuitBreakerService.state(source).name()
                ))
                .toList();
    }

    public record ProviderHealthSnapshot(
            DataSource source,
            String circuitBreakerState,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            int failedMappingCount,
            int totalMappingCount
    ) {
    }

    private static final class ProviderHealthAccumulator {
        private final DataSource source;
        private Instant lastSuccessAt;
        private Instant lastFailureAt;
        private int failedMappingCount;
        private int totalMappingCount;

        private ProviderHealthAccumulator(DataSource source) {
            this.source = source;
        }

        private void accept(MarketProviderMappingEntity mapping) {
            totalMappingCount++;
            if (mapping.getLastRefreshStatus() == MappingRefreshStatus.FAILED) {
                failedMappingCount++;
            }
            if (mapping.getLastRefreshedAt() != null && (lastSuccessAt == null || mapping.getLastRefreshedAt().isAfter(lastSuccessAt))) {
                lastSuccessAt = mapping.getLastRefreshedAt();
            }
            if (mapping.getLastFailedAt() != null && (lastFailureAt == null || mapping.getLastFailedAt().isAfter(lastFailureAt))) {
                lastFailureAt = mapping.getLastFailedAt();
            }
        }

        private ProviderHealthSnapshot snapshot(String circuitBreakerState) {
            return new ProviderHealthSnapshot(source, circuitBreakerState, lastSuccessAt, lastFailureAt, failedMappingCount, totalMappingCount);
        }
    }
}
