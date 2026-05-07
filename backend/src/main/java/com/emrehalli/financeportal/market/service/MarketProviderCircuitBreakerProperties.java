package com.emrehalli.financeportal.market.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.providers.circuit-breaker")
public class MarketProviderCircuitBreakerProperties {

    private int slidingWindowSize = 5;
    private float failureRateThreshold = 60.0f;
    private long waitDurationOpenStateSeconds = 30L;
    private int permittedCallsInHalfOpenState = 2;

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
}
