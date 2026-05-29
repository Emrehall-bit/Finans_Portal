package com.emrehalli.financeportal.market.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockHistoryStartupRunner {

    private final StockHistoryScheduler stockHistoryScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        Thread thread = new Thread(() -> {
            log.info("StockHistoryStartupRunner: startup catch-up started");
            try {
                stockHistoryScheduler.fetch();
                log.info("StockHistoryStartupRunner: startup catch-up finished");
            } catch (Exception e) {
                log.error("StockHistoryStartupRunner: startup catch-up failed", e);
            }
        }, "stock-history-startup");
        thread.setDaemon(true);
        thread.start();
    }
}
