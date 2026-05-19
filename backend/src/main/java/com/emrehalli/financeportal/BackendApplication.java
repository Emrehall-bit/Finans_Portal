package com.emrehalli.financeportal;

import com.emrehalli.financeportal.ai.config.AiProperties;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
import com.emrehalli.financeportal.news.provider.cnbc.CnbcNewsProperties;
import com.emrehalli.financeportal.news.provider.finnhub.FinnhubProperties;
import com.emrehalli.financeportal.news.provider.investing.InvestingNewsProperties;
import com.emrehalli.financeportal.news.provider.kap.KapNewsProperties;
import com.emrehalli.financeportal.news.provider.reuters.ReutersNewsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableConfigurationProperties({
        AiProperties.class,
        AaNewsProperties.class,
        CnbcNewsProperties.class,
        FinnhubProperties.class,
        InvestingNewsProperties.class,
        KapNewsProperties.class,
        ReutersNewsProperties.class
})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
