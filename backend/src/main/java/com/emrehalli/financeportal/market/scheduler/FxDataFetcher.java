package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.provider.fx.AkbankFxProvider;
import com.emrehalli.financeportal.market.provider.fx.TcmbFxProvider;
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

    private final TcmbFxProvider tcmbFxProvider;
    private final AkbankFxProvider akbankFxProvider;
    private final ZiraatFxProvider ziraatFxProvider;
    private final FxService fxService;

    @Scheduled(initialDelay = 0, fixedRateString = "${market.scheduler.fx-rate-ms:1800000}")
    public void fetch() {
        try {
            List<FxRateDto> rates = tcmbFxProvider.fetch();
            saveRates("TCMB", rates);
        } catch (Exception exception) {
            log.error("Failed to fetch FX data from provider TCMB", exception);
        }

        try {
            List<FxRateDto> rates = akbankFxProvider.fetch();
            saveRates("AKBANK", rates);
        } catch (Exception exception) {
            log.error("Failed to fetch FX data from provider AKBANK", exception);
        }

        try {
            List<FxRateDto> rates = ziraatFxProvider.fetch();
            saveRates("ZIRAAT", rates);
        } catch (Exception exception) {
            log.error("Failed to fetch FX data from provider ZIRAAT", exception);
        }
    }

    private void saveRates(String sourceName, List<FxRateDto> rates) {
        try {
            if (rates != null && !rates.isEmpty()) {
                fxService.saveAll(rates);
            }
        } catch (Exception exception) {
            log.error("Failed to save FX data for provider {}", sourceName, exception);
        }
    }
}
