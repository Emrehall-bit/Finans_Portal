package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.scheduler.StockDataFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockFetchAsyncService {

    private final StockDataFetcher stockDataFetcher;
    private final StockFetchStatus stockFetchStatus;

    @Async
    public void runFetchAsync() {
        stockFetchStatus.start(1);
        try {
            stockDataFetcher.fetch();
            stockFetchStatus.incrementProcessed();
        } catch (Exception exception) {
            log.error("Manual stock fetch failed.", exception);
        } finally {
            stockFetchStatus.finish();
        }
    }
}



