package com.emrehalli.financeportal.market.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties(prefix = "market.providers.circuit-breaker")
public class MarketProviderCircuitBreakerProperties {

    private int slidingWindowSize = 5;
    private float failureRateThreshold = 60.0f;
    private long waitDurationOpenStateSeconds = 30L;
    private int permittedCallsInHalfOpenState = 2;
    private Map<String, InstanceProperties> instances = Map.of();

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public void setSlidingWindowSize(int slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
    }

    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public void setFailureRateThreshold(float failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public long getWaitDurationOpenStateSeconds() {
        return waitDurationOpenStateSeconds;
    }

    public void setWaitDurationOpenStateSeconds(long waitDurationOpenStateSeconds) {
        this.waitDurationOpenStateSeconds = waitDurationOpenStateSeconds;
    }

    public int getPermittedCallsInHalfOpenState() {
        return permittedCallsInHalfOpenState;
    }

    public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) {
        this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState;
    }

    public Map<String, InstanceProperties> getInstances() {
        return instances;
    }

    public void setInstances(Map<String, InstanceProperties> instances) {
        this.instances = instances == null ? Map.of() : Map.copyOf(instances);
    }

    public ResolvedProperties resolvedFor(String sourceName) {
        InstanceProperties instance = Optional.ofNullable(sourceName)
                .map(value -> instances.get(value.toLowerCase(Locale.ROOT)))
                .orElse(null);

        return new ResolvedProperties(
                instance != null && instance.getSlidingWindowSize() != null ? instance.getSlidingWindowSize() : slidingWindowSize,
                instance != null && instance.getFailureRateThreshold() != null ? instance.getFailureRateThreshold() : failureRateThreshold,
                instance != null && instance.getWaitDurationOpenStateSeconds() != null ? instance.getWaitDurationOpenStateSeconds() : waitDurationOpenStateSeconds,
                instance != null && instance.getPermittedCallsInHalfOpenState() != null ? instance.getPermittedCallsInHalfOpenState() : permittedCallsInHalfOpenState
        );
    }

    public static class InstanceProperties {
        private Integer slidingWindowSize;
        private Float failureRateThreshold;
        private Long waitDurationOpenStateSeconds;
        private Integer permittedCallsInHalfOpenState;

        public Integer getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(Integer slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public Float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(Float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public Long getWaitDurationOpenStateSeconds() {
            return waitDurationOpenStateSeconds;
        }

        public void setWaitDurationOpenStateSeconds(Long waitDurationOpenStateSeconds) {
            this.waitDurationOpenStateSeconds = waitDurationOpenStateSeconds;
        }

        public Integer getPermittedCallsInHalfOpenState() {
            return permittedCallsInHalfOpenState;
        }

        public void setPermittedCallsInHalfOpenState(Integer permittedCallsInHalfOpenState) {
            this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState;
        }
    }

    public record ResolvedProperties(
            int slidingWindowSize,
            float failureRateThreshold,
            long waitDurationOpenStateSeconds,
            int permittedCallsInHalfOpenState
    ) {
    }
}
