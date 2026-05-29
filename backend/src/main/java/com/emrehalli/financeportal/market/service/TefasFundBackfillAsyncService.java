package com.emrehalli.financeportal.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TefasFundBackfillAsyncService {

    private final TefasFundHistoricalBackfillService tefasFundHistoricalBackfillService;
    private final TefasFundBackfillStatus tefasFundBackfillStatus;

    @Async
    public void runAsync(String fundCode, Integer periyod) {
        tefasFundBackfillStatus.start();
        try {
            TefasFundHistoricalBackfillService.BackfillResult result =
                    tefasFundHistoricalBackfillService.runBackfill(fundCode, periyod);
            tefasFundBackfillStatus.finish(result);
        } catch (Exception exception) {
            log.error("Manual TEFAS fund history backfill failed. fundCode={}, periyod={}", fundCode, periyod, exception);
            tefasFundBackfillStatus.fail(exception.getMessage());
        }
    }
}




