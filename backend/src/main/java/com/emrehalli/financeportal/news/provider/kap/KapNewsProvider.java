package com.emrehalli.financeportal.news.provider.kap;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class KapNewsProvider implements NewsProvider {

    private static final Logger logger = LogManager.getLogger(KapNewsProvider.class);

    private final KapNewsClient kapNewsClient;
    private final KapNewsProperties properties;

    public KapNewsProvider(KapNewsClient kapNewsClient, KapNewsProperties properties) {
        this.kapNewsClient = kapNewsClient;
        this.properties = properties;
    }

    @Override
    public String getProviderName() {
        return "KAP";
    }

    @Override
    public List<NewsItemDto> fetchLatestNews() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(properties.getLookbackDays());
        return kapNewsClient.fetchDisclosures(from, to);
    }

    @Override
    public List<NewsItemDto> fetchCompanyNews(String symbol) {
        if (!properties.isEnabled() || symbol == null || symbol.isBlank()) {
            return List.of();
        }
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(properties.getLookbackDays());
        return kapNewsClient.fetchDisclosures(from, to, normalizedSymbol);
    }

    /**
     * Fetches disclosures for an explicit date range, splitting into chunks
     * of maxRangeDays when the range exceeds the limit.
     * Used by admin backfill and startup sync.
     */
    public List<NewsItemDto> fetchDisclosures(LocalDate fromDate, LocalDate toDate) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        int maxRange = properties.getMaxRangeDays();
        List<NewsItemDto> results = new ArrayList<>();
        LocalDate chunkStart = fromDate;

        while (!chunkStart.isAfter(toDate)) {
            LocalDate chunkEnd = chunkStart.plusDays(maxRange - 1);
            if (chunkEnd.isAfter(toDate)) {
                chunkEnd = toDate;
            }

            logger.info("KAP backfill chunk. from: {}, to: {}", chunkStart, chunkEnd);
            List<NewsItemDto> chunk = kapNewsClient.fetchDisclosures(chunkStart, chunkEnd);
            results.addAll(chunk);

            chunkStart = chunkEnd.plusDays(1);
        }

        return results;
    }

    public List<NewsItemDto> fetchStartupDisclosures() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(properties.getStartupLookbackDays());
        logger.info("KAP startup sync. from: {}, to: {}, startupLookbackDays: {}", from, to, properties.getStartupLookbackDays());
        return kapNewsClient.fetchDisclosures(from, to);
    }
}
