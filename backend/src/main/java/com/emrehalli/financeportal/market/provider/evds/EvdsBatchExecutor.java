package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EvdsBatchExecutor {

    private static final Logger log = LoggerFactory.getLogger(EvdsBatchExecutor.class);
    private static final int DEFAULT_BATCH_SIZE = 5;

    private final EvdsClient evdsClient;
    private final EvdsFallbackRetryService fallbackRetryService;

    public EvdsBatchExecutor(EvdsClient evdsClient,
                             EvdsFallbackRetryService fallbackRetryService) {
        this.evdsClient = evdsClient;
        this.fallbackRetryService = fallbackRetryService;
    }

    public ExecutionResult execute(List<EvdsRequestBuilder.EvdsSeriesRequest> requests,
                                   LocalDate from,
                                   LocalDate to) {
        if (requests == null || requests.isEmpty()) {
            return new ExecutionResult(List.of(), List.of(), DEFAULT_BATCH_SIZE, 0);
        }

        List<SuccessfulPayload> successfulPayloads = new ArrayList<>();
        List<String> failedSeriesCodes = new ArrayList<>();
        int fallbackRetryCount = 0;

        for (List<EvdsSeriesGroup> batch : partition(groupRequests(requests), DEFAULT_BATCH_SIZE)) {
            List<String> batchRequestCodes = batch.stream()
                    .map(EvdsSeriesGroup::requestSeriesCode)
                    .toList();

            try {
                EvdsResponse response = evdsClient.fetchSeries(batchRequestCodes, from, to);
                successfulPayloads.add(new SuccessfulPayload(
                        response,
                        batch.stream()
                                .flatMap(group -> group.requests().stream())
                                .map(EvdsRequestBuilder.EvdsSeriesRequest::seriesConfig)
                                .toList(),
                        batchRequestCodes
                ));
            } catch (Exception ex) {
                log.warn("EVDS batch request failed. requestSeriesCodes={}, batchSize={}, error={}",
                        batchRequestCodes,
                        batchRequestCodes.size(),
                        ex.getMessage());
                EvdsFallbackRetryService.RetryResult retryResult = fallbackRetryService.retryIndividually(batch, from, to);
                successfulPayloads.addAll(retryResult.successfulPayloads());
                failedSeriesCodes.addAll(retryResult.failedSeriesCodes());
                fallbackRetryCount += retryResult.fallbackRetryCount();
            }
        }

        return new ExecutionResult(
                List.copyOf(successfulPayloads),
                List.copyOf(failedSeriesCodes),
                DEFAULT_BATCH_SIZE,
                fallbackRetryCount
        );
    }

    private List<EvdsSeriesGroup> groupRequests(List<EvdsRequestBuilder.EvdsSeriesRequest> requests) {
        Map<String, List<EvdsRequestBuilder.EvdsSeriesRequest>> requestsByRequestCode = new LinkedHashMap<>();
        for (EvdsRequestBuilder.EvdsSeriesRequest request : requests) {
            requestsByRequestCode.computeIfAbsent(request.requestSeriesCode(), ignored -> new ArrayList<>())
                    .add(request);
        }
        return requestsByRequestCode.entrySet().stream()
                .map(entry -> new EvdsSeriesGroup(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private List<List<EvdsSeriesGroup>> partition(List<EvdsSeriesGroup> groups, int batchSize) {
        List<List<EvdsSeriesGroup>> batches = new ArrayList<>();
        for (int index = 0; index < groups.size(); index += batchSize) {
            batches.add(List.copyOf(groups.subList(index, Math.min(index + batchSize, groups.size()))));
        }
        return List.copyOf(batches);
    }

    public record EvdsSeriesGroup(
            String requestSeriesCode,
            List<EvdsRequestBuilder.EvdsSeriesRequest> requests
    ) {
    }

    public record SuccessfulPayload(
            EvdsResponse response,
            List<com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties.SeriesConfig> seriesConfigs,
            List<String> requestSeriesCodes
    ) {
    }

    public record ExecutionResult(
            List<SuccessfulPayload> successfulPayloads,
            List<String> failedSeriesCodes,
            int batchSize,
            int fallbackRetryCount
    ) {
    }
}
