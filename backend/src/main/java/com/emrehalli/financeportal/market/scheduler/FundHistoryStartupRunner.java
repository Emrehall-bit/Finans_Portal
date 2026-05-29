package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.service.TefasFundHistoricalBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class FundHistoryStartupRunner {

    private final TefasFundHistoricalBackfillService backfillService;
    private final MarketInstrumentRepository instrumentRepository;

    private final ExecutorService executor = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "fund-history-pool");
        t.setDaemon(true);
        return t;
    });

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        Thread thread = new Thread(this::runCatchUp, "fund-history-startup");
        thread.setDaemon(true);
        thread.start();
    }

    private void runCatchUp() {
        List<String> fundCodes = instrumentRepository
                .findAllByInstrumentTypeAndSourceName(InstrumentType.FUND, SourceName.TEFAS)
                .stream()
                .map(i -> i.getInstrumentCode())
                .toList();

        if (fundCodes.isEmpty()) {
            log.info("FundHistoryStartupRunner: DB'de fon bulunamadı, atlanıyor.");
            return;
        }

        log.info("FundHistoryStartupRunner: startup catch-up başlıyor. fonSayısı={}", fundCodes.size());
        AtomicInteger totalSaved = new AtomicInteger(0);

        List<Future<Void>> futures = fundCodes.stream()
                .<Future<Void>>map(code -> executor.submit(() -> {
                    try {
                        TefasFundHistoricalBackfillService.BackfillResult result =
                                backfillService.runBackfill(code, null);
                        totalSaved.addAndGet(result.savedRecords());
                    } catch (Exception e) {
                        log.warn("FundHistoryStartupRunner: fon catch-up hatası. fonKodu={}", code, e);
                    }
                    return null;
                }))
                .toList();

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.warn("FundHistoryStartupRunner: future hatası", e);
            }
        }

        log.info("FundHistoryStartupRunner: startup catch-up tamamlandı. toplamKaydedilen={}", totalSaved.get());
    }
}
