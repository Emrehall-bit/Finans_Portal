package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.scheduler.FundDataFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundFetchAsyncService {

    private final FundDataFetcher fundDataFetcher;
    private final FundFetchStatus fundFetchStatus;

    @Async
    public void runAsync() {
        fundFetchStatus.start();
        try {
            FundDataFetcher.FundFetchResult result = fundDataFetcher.fetchNow();
            fundFetchStatus.finish(result.processedFunds(), result.savedFunds());
        } catch (Exception exception) {
            log.error("Manual TEFAS fund fetch failed.", exception);
            fundFetchStatus.fail(exception.getMessage());
        }
    }
}



