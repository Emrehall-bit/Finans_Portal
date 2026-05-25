package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.provider.bond.BondDataProvider;
import com.emrehalli.financeportal.market.provider.bond.dto.BondRateDto;
import com.emrehalli.financeportal.market.service.BondService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for fetching bond market data.
 */
@Component
@Slf4j
@AllArgsConstructor
public class BondDataFetcher {

    private final BondDataProvider bondDataProvider;
    private final BondService bondService;

    @Scheduled(cron = "${market.scheduler.bond-cron:0 0 19 * * MON-FRI}")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("BondDataFetcher.fetch");
        int processedCount = 0;
        int successCount = 0;
        try {
            List<BondRateDto> rates = bondDataProvider.fetch();
            processedCount = rates.size();
            if (!rates.isEmpty()) {
                bondService.saveAll(rates);
                successCount = rates.size();
            }
            run.log(log, processedCount, successCount, 0);
        } catch (Exception exception) {
            log.error("Failed to fetch bond data from TCMB", exception);
            run.log(log, processedCount, successCount, processedCount == 0 ? 1 : processedCount - successCount, exception);
        }
    }
}



