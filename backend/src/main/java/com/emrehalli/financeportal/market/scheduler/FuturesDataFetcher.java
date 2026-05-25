package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.provider.futures.ViopDataProvider;
import com.emrehalli.financeportal.market.provider.futures.dto.FuturesContractDto;
import com.emrehalli.financeportal.market.service.FuturesService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for fetching futures market data.
 */
@Component
@Slf4j
@AllArgsConstructor
public class FuturesDataFetcher {

    private final ViopDataProvider viopDataProvider;
    private final FuturesService futuresService;

    @Scheduled(cron = "${market.scheduler.futures-cron:0 0 19 * * MON-FRI}")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("FuturesDataFetcher.fetch");
        int processedCount = 0;
        int successCount = 0;
        try {
            List<FuturesContractDto> contracts = viopDataProvider.fetch();
            processedCount = contracts.size();
            if (!contracts.isEmpty()) {
                futuresService.saveAll(contracts);
                successCount = contracts.size();
            }
            run.log(log, processedCount, successCount, 0);
        } catch (Exception exception) {
            log.error("Failed to fetch futures data from BIST", exception);
            run.log(log, processedCount, successCount, processedCount == 0 ? 1 : processedCount - successCount, exception);
        }
    }
}



