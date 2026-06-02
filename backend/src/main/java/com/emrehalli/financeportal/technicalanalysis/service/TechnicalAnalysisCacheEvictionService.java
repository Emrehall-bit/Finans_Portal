package com.emrehalli.financeportal.technicalanalysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TechnicalAnalysisCacheEvictionService {

    private static final String TECHNICAL_ANALYSIS_CACHE = "technicalAnalysis";
    private static final String INSTRUMENT_COMPARISON_CACHE = "instrumentComparison";

    private final CacheManager cacheManager;

    public void evictTechnicalAnalysisCaches() {
        clearCache(TECHNICAL_ANALYSIS_CACHE);
    }

    public void evictInstrumentComparisonCaches() {
        clearCache(INSTRUMENT_COMPARISON_CACHE);
    }

    public void evictAllTechnicalCaches() {
        evictTechnicalAnalysisCaches();
        evictInstrumentComparisonCaches();
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Technical analysis cache eviction skipped because cache is missing. cacheName={}", cacheName);
            return;
        }

        cache.clear();
        log.info("Technical analysis cache cleared. cacheName={}", cacheName);
    }
}
