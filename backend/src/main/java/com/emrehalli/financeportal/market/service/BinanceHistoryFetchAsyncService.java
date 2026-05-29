package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.scheduler.BinanceHistoryFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BinanceHistoryFetchAsyncService {

    private final BinanceHistoryFetcher binanceHistoryFetcher;
    private final BinanceHistoryFetchStatus binanceHistoryFetchStatus;

    @Async
    public void runFetchAsync(int days) {
        binanceHistoryFetchStatus.start(1);
        try {
            binanceHistoryFetcher.fetchHistoryManual(days);
            binanceHistoryFetchStatus.incrementProcessed();
        } catch (Exception exception) {
            log.error("Manual Binance history fetch failed. days={}", days, exception);
        } finally {
            binanceHistoryFetchStatus.finish();
        }
    }
}




