package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.provider.fx.AkbankFxProvider;
import com.emrehalli.financeportal.market.provider.fx.ZiraatFxProvider;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.emrehalli.financeportal.market.service.FxService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for fetching FX data from external providers.
 */
@Component
@Slf4j
@AllArgsConstructor
public class FxDataFetcher {

    private final AkbankFxProvider akbankFxProvider;
    private final ZiraatFxProvider ziraatFxProvider;
    private final FxService fxService;

    @Scheduled(initialDelay = 0, fixedRateString = "${market.scheduler.fx-rate-ms:1800000}")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("FxDataFetcher.fetch");
        int processedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        try {
            List<FxRateDto> rates = akbankFxProvider.fetch();
            processedCount += count(rates);
            successCount += saveRates("AKBANK", rates);
        } catch (Exception exception) {
            failedCount++;
            log.warn("Failed to fetch FX data from provider AKBANK. errorMessage={}", exception.getMessage());
            log.debug("Failed to fetch FX data from provider AKBANK", exception);
        }

        try {
            List<FxRateDto> rates = ziraatFxProvider.fetch();
            processedCount += count(rates);
            successCount += saveRates("ZIRAAT", rates);
        } catch (Exception exception) {
            failedCount++;
            log.warn("Failed to fetch FX data from provider ZIRAAT. errorMessage={}", exception.getMessage());
            log.debug("Failed to fetch FX data from provider ZIRAAT", exception);
        }

        run.log(log, processedCount, successCount, failedCount);
    }

    private int saveRates(String sourceName, List<FxRateDto> rates) {
        try {
            if (rates != null && !rates.isEmpty()) {
                fxService.saveAll(rates);
                return rates.size();
            }
        } catch (Exception exception) {
            log.warn("Failed to save FX data for provider {}. errorMessage={}", sourceName, exception.getMessage());
            log.debug("Failed to save FX data for provider {}", sourceName, exception);
        }
        return 0;
    }

    private int count(List<FxRateDto> rates) {
        return rates == null ? 0 : rates.size();
    }
}
