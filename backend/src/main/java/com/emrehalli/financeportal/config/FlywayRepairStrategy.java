package com.emrehalli.financeportal.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairStrategy {

    private static final Logger logger = LogManager.getLogger(FlywayRepairStrategy.class);

    @Bean
    public FlywayMigrationStrategy repairAndMigrate() {
        return flyway -> {
            logger.info("Flyway repair starting â€” aligning schema history checksums");
            flyway.repair();
            logger.info("Flyway repair complete â€” running migrations");
            flyway.migrate();
        };
    }
}

