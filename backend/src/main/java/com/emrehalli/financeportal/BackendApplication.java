package com.emrehalli.financeportal;

import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.tefas.config.TefasProperties;
import com.emrehalli.financeportal.market.scheduler.MarketRefreshProperties;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillProperties;
import com.emrehalli.financeportal.market.service.MarketProviderCircuitBreakerProperties;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
import com.emrehalli.financeportal.news.provider.finnhub.FinnhubProperties;
import com.emrehalli.financeportal.news.provider.investing.InvestingNewsProperties;
import com.emrehalli.financeportal.news.provider.kap.KapNewsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableConfigurationProperties({
        AaNewsProperties.class,
        FinnhubProperties.class,
        InvestingNewsProperties.class,
        KapNewsProperties.class,
        MarketRefreshProperties.class,
        MarketHistoryBackfillProperties.class,
        MarketProviderCircuitBreakerProperties.class,
        EvdsProperties.class,
        BinanceProviderProperties.class,
        TefasProperties.class
})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
