package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class EvdsFallbackRetryService {

    private static final Logger log = LoggerFactory.getLogger(EvdsFallbackRetryService.class);

    private final EvdsClient evdsClient;

    public EvdsFallbackRetryService(EvdsClient evdsClient) {
        this.evdsClient = evdsClient;
    }

    public RetryResult retryIndividually(List<EvdsBatchExecutor.EvdsSeriesGroup> failedGroups,
                                         LocalDate from,
                                         LocalDate to) {
        List<EvdsBatchExecutor.SuccessfulPayload> successfulPayloads = new ArrayList<>();
        List<String> failedSeriesCodes = new ArrayList<>();
        int fallbackRetryCount = 0;

        for (EvdsBatchExecutor.EvdsSeriesGroup group : failedGroups) {
            fallbackRetryCount++;
            try {
                EvdsResponse response = evdsClient.fetchSeries(List.of(group.requestSeriesCode()), from, to);
                successfulPayloads.add(new EvdsBatchExecutor.SuccessfulPayload(
                        response,
                        group.requests().stream()
                                .map(EvdsRequestBuilder.EvdsSeriesRequest::seriesConfig)
                                .toList(),
                        List.of(group.requestSeriesCode())
                ));
            } catch (Exception ex) {
                List<String> failedCodes = group.requests().stream()
                        .map(EvdsRequestBuilder.EvdsSeriesRequest::originalSeriesCode)
                        .distinct()
                        .toList();
                failedSeriesCodes.addAll(failedCodes);
                log.warn("EVDS single fallback failed: requestSeriesCode={}, failedSeriesCodes={}, error={}",
                        group.requestSeriesCode(),
                        failedCodes,
                        ex.getMessage());
            }
        }

        return new RetryResult(List.copyOf(successfulPayloads), List.copyOf(failedSeriesCodes), fallbackRetryCount);
    }

    public record RetryResult(
            List<EvdsBatchExecutor.SuccessfulPayload> successfulPayloads,
            List<String> failedSeriesCodes,
            int fallbackRetryCount
    ) {
    }
}
