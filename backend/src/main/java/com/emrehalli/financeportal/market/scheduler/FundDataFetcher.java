package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.provider.fund.TefasProvider;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import com.emrehalli.financeportal.market.service.FundService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for fetching fund NAV data.
 */
@Component
@Slf4j
@AllArgsConstructor
public class FundDataFetcher {

    private final TefasProvider tefasProvider;
    private final FundService fundService;

    @Scheduled(cron = "${market.scheduler.fund-cron:0 30 18 * * MON-FRI}")
    public void fetch() {
        fetchNow();
    }

    public FundFetchResult fetchNow() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("FundDataFetcher.fetch");
        int processedCount = 0;
        int successCount = 0;
        try {
            List<FundNavDto> funds = tefasProvider.fetch();
            processedCount = funds.size();
            if (funds.isEmpty()) {
                log.warn("TEFAS fund fetcher received no fund NAV data. saveAll skipped.");
                run.log(log, 0, 0, 0);
                return new FundFetchResult(0, 0);
            }
            fundService.saveAll(funds);
            successCount = funds.size();
            run.log(log, processedCount, successCount, 0);
            return new FundFetchResult(funds.size(), funds.size());
        } catch (Exception exception) {
            log.error("Failed to fetch fund NAV data from TEFAS", exception);
            run.log(log, processedCount, successCount, processedCount == 0 ? 1 : processedCount - successCount, exception);
            throw exception;
        }
    }

    public record FundFetchResult(
            int processedFunds,
            int savedFunds
    ) {
    }
}
