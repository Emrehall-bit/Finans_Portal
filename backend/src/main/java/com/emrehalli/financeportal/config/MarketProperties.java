package com.emrehalli.financeportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration for market module properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "market")
public class MarketProperties {

    private Providers providers = new Providers();
    private Scheduler scheduler = new Scheduler();
    private Cache cache = new Cache();

    @Data
    public static class Providers {
        private TcmbProvider tcmb = new TcmbProvider();
        private Provider akbank = new Provider();
        private Provider ziraat = new Provider();
        private Provider binance = new Provider();
        private Provider yahoo = new Provider();
        private Provider isyatirim = new Provider();
        private Provider bist = new Provider();
        private Provider tefas = new Provider();
    }

    @Data
    public static class Provider {
        private String baseUrl;
        private String apiKey;
        private String cookie;
        private String crumb;
    }

    @Data
    public static class TcmbProvider extends Provider {
        private String startDate;
        private String endDate;
        private boolean currentSyncEnabled = true;
        private long currentSyncFixedRateMs = 3600000;
        private boolean startupBackfillEnabled = true;
        private boolean historicalSchedulerEnabled = false;
    }

    @Data
    public static class Scheduler {
        private long cryptoRateMs = 300000;
        private String fundCron = "0 30 18 * * MON-FRI";
        private long stockRateMs = 1800000;
        private String futuresCron = "0 0 19 * * MON-FRI";
        private String bondCron = "0 0 19 * * MON-FRI";
    }

    @Data
    public static class Cache {
        private TtlMinutes ttlMinutes = new TtlMinutes();
    }

    @Data
    public static class TtlMinutes {
        private long fx = 40;
        private long crypto = 8;
        private long fund = 1500;
        private long stock = 40;
        private long futures = 1500;
        private long bond = 1500;
        private long index = 40;
        private long commodity = 40;
    }
}




