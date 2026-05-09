package com.emrehalli.financeportal.market.scheduler;

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

    @Scheduled(fixedRateString = "${market.scheduler.crypto-rate-ms:300000}")
    public void fetch() {
        try {
            List<CryptoTickerDto> tickers = binanceProvider.fetch();
            if (!tickers.isEmpty()) {
                cryptoService.saveAll(tickers);
            }
        } catch (Exception exception) {
            log.error("Failed to fetch crypto data from Binance", exception);
        }
    }
}
