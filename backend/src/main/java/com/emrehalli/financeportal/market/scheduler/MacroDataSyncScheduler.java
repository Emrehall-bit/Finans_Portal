package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.service.MacroDataSyncService;
import com.emrehalli.financeportal.market.service.MacroSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market.scheduler.macro-enabled", havingValue = "true", matchIfMissing = true)
public class MacroDataSyncScheduler {

    private final MacroDataSyncService macroDataSyncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${market.scheduler.macro-lookback-days:120}")
    private int lookbackDays;

    @Scheduled(cron = "${market.scheduler.macro-cron:0 20 3 10,20 * *}", zone = "${market.scheduler.macro-zone:Europe/Istanbul}")
    public void syncRecentMacroData() {
        if (!running.compareAndSet(false, true)) {
            log.warn("MacroDataSyncScheduler skipped because a previous macro sync is still running.");
            return;
        }

        try {
            Map<String, MacroSyncResult> results = macroDataSyncService.syncRecentFromTcmb(lookbackDays);
            int fetched = results.values().stream().mapToInt(MacroSyncResult::fetched).sum();
            int saved = results.values().stream().mapToInt(MacroSyncResult::saved).sum();
            int duplicates = results.values().stream().mapToInt(MacroSyncResult::duplicates).sum();
            int skipped = results.values().stream().mapToInt(MacroSyncResult::skipped).sum();
            log.info(
                    "MacroDataSyncScheduler completed. lookbackDays={}, fetched={}, saved={}, duplicates={}, skipped={}",
                    lookbackDays,
                    fetched,
                    saved,
                    duplicates,
                    skipped
            );
        } catch (Exception exception) {
            log.error("MacroDataSyncScheduler failed. lookbackDays={}", lookbackDays, exception);
        } finally {
            running.set(false);
        }
    }
}
