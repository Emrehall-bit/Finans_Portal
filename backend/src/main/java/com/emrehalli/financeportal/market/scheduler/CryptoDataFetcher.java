package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.market.provider.crypto.BinanceProvider;
import com.emrehalli.financeportal.market.provider.crypto.dto.CryptoTickerDto;
import com.emrehalli.financeportal.market.service.CryptoService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for fetching crypto market data.
 */
@Component
@Slf4j
@AllArgsConstructor
public class CryptoDataFetcher {

    private final BinanceProvider binanceProvider;
    private final CryptoService cryptoService;

    @Scheduled(fixedRateString = "${market.scheduler.crypto-rate-ms:600000}")
    public void fetch() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("CryptoDataFetcher.fetch");
        int processedCount = 0;
        int successCount = 0;
        try {
            List<CryptoTickerDto> tickers = binanceProvider.fetch();
            processedCount = tickers.size();
            if (!tickers.isEmpty()) {
                cryptoService.saveAll(tickers);
                successCount = tickers.size();
            }
            run.log(log, processedCount, successCount, 0);
        } catch (Exception exception) {
            log.error("Failed to fetch crypto data from Binance", exception);
            run.log(log, processedCount, successCount, processedCount == 0 ? 1 : processedCount - successCount, exception);
        }
    }
}
