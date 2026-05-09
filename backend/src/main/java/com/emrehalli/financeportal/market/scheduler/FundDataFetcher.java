package com.emrehalli.financeportal.market.scheduler;

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
        try {
            List<FundNavDto> funds = tefasProvider.fetch();
            if (funds.isEmpty()) {
                log.warn("TEFAS fund fetcher received no fund NAV data. saveAll skipped.");
                return;
            }
            fundService.saveAll(funds);
        } catch (Exception exception) {
            log.error("Failed to fetch fund NAV data from TEFAS", exception);
        }
    }
}
