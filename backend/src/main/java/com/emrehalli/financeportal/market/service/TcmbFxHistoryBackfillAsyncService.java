package com.emrehalli.financeportal.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TcmbFxHistoryBackfillAsyncService {

    private final TcmbFxHistoricalBackfillService tcmbFxHistoricalBackfillService;
    private final TcmbFxHistoryBackfillStatus tcmbFxHistoryBackfillStatus;

    @Async
    public void runBackfillAsync() {
        tcmbFxHistoryBackfillStatus.start(1);
        try {
            tcmbFxHistoricalBackfillService.backfillAll();
            tcmbFxHistoryBackfillStatus.incrementProcessed();
        } catch (Exception exception) {
            log.error("Manual TCMB FX history backfill failed.", exception);
        } finally {
            tcmbFxHistoryBackfillStatus.finish();
        }
    }
}



