package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.news.service.SystemGeneratedNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SystemGeneratedNewsScheduler {

    private static final List<InstrumentType> MONITORED_TYPES = List.of(
            InstrumentType.CRYPTO,
            InstrumentType.STOCK,
            InstrumentType.FX,
            InstrumentType.COMMODITY,
            InstrumentType.FUND
    );

    private final SystemGeneratedNewsService systemGeneratedNewsService;

    @Scheduled(fixedRateString = "${news.system-generated.check-rate-ms:1800000}")
    public void checkSignificantMovements() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("SystemGeneratedNewsScheduler.checkSignificantMovements");
        int total = 0;
        try {
            for (InstrumentType type : MONITORED_TYPES) {
                total += systemGeneratedNewsService.checkAndGenerateForType(type);
            }
            run.log(log, MONITORED_TYPES.size(), total, 0);
        } catch (Exception ex) {
            log.error("System generated news scheduler failed.", ex);
            run.log(log, MONITORED_TYPES.size(), total, 1, ex);
        }
    }
}
