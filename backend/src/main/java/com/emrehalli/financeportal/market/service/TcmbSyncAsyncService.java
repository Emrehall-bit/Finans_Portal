package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.scheduler.TcmbFxCurrentPriceScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TcmbSyncAsyncService {

    private final TcmbFxCurrentPriceScheduler tcmbFxCurrentPriceScheduler;
    private final TcmbSyncStatus tcmbSyncStatus;

    @Async
    public void runSyncAsync() {
        tcmbSyncStatus.start(1);
        try {
            tcmbFxCurrentPriceScheduler.sync();
            tcmbSyncStatus.incrementProcessed();
        } catch (Exception exception) {
            log.error("Manual TCMB sync failed.", exception);
        } finally {
            tcmbSyncStatus.finish();
        }
    }
}



