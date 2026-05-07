package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class ProviderCircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<DataSource, CircuitBreaker> circuitBreakers;
    private final Map<DataSource, Instant> openSinceBySource = new java.util.concurrent.ConcurrentHashMap<>();
    private final Clock clock;

    public ProviderCircuitBreakerService(List<MarketDataProvider> providers,
                                         MarketProviderCircuitBreakerProperties properties,
                                         Clock clock) {
        this.clock = clock;
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(buildConfig(properties));
        this.circuitBreakers = new EnumMap<>(DataSource.class);

        providers.stream()
                .map(MarketDataProvider::source)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .forEach(this::register);
    }

    public <T> T execute(DataSource source, Supplier<T> supplier) {
        return circuitBreaker(source).executeSupplier(supplier);
    }

    public CircuitBreaker.State state(DataSource source) {
        return circuitBreaker(source).getState();
    }

    public Optional<Instant> openSince(DataSource source) {
        return Optional.ofNullable(openSinceBySource.get(source));
    }

    public List<DataSource> sources() {
        return circuitBreakers.keySet().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private void register(DataSource source) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(source.name().toLowerCase());
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                openSinceBySource.put(source, clock.instant());
                return;
            }
            openSinceBySource.remove(source);
        });
        circuitBreakers.put(source, circuitBreaker);
    }

    private CircuitBreaker circuitBreaker(DataSource source) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(source);
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("No circuit breaker configured for provider source: " + source);
        }
        return circuitBreaker;
    }

    private CircuitBreakerConfig buildConfig(MarketProviderCircuitBreakerProperties properties) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(Math.max(properties.getSlidingWindowSize(), 1))
                .minimumNumberOfCalls(Math.max(properties.getSlidingWindowSize(), 1))
                .failureRateThreshold(properties.getFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(Math.max(properties.getWaitDurationOpenStateSeconds(), 1L)))
                .permittedNumberOfCallsInHalfOpenState(Math.max(properties.getPermittedCallsInHalfOpenState(), 1))
                .build();
    }
}
