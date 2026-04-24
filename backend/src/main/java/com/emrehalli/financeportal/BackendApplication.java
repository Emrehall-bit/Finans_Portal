package com.emrehalli.financeportal;

import org.springframework.boot.SpringApplication;
import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.tefas.config.TefasProviderProperties;
import com.emrehalli.financeportal.market.scheduler.MarketRefreshProperties;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
import com.emrehalli.financeportal.news.provider.finnhub.FinnhubProperties;
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
        MarketRefreshProperties.class,
        EvdsProperties.class,
        BinanceProviderProperties.class,
        TefasProviderProperties.class
})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
