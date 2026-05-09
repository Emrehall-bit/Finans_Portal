package com.emrehalli.financeportal.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class
            ))
            .withUserConfiguration(MarketProperties.class);

    @Test
    void bindsTcmbProviderProperties() {
        contextRunner
                .withPropertyValues(
                        "market.providers.tcmb.base-url=https://evds3.tcmb.gov.tr/igmevdsms-dis",
                        "market.providers.tcmb.api-key=test-local-key",
                        "market.providers.tcmb.start-date=01-01-2000",
                        "market.providers.tcmb.end-date=01-01-2999"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MarketProperties.class);

                    MarketProperties properties = context.getBean(MarketProperties.class);
                    assertThat(properties.getProviders().getTcmb().getBaseUrl())
                            .isEqualTo("https://evds3.tcmb.gov.tr/igmevdsms-dis");
                    assertThat(properties.getProviders().getTcmb().getApiKey())
                            .isEqualTo("test-local-key");
                    assertThat(properties.getProviders().getTcmb().getStartDate())
                            .isEqualTo("01-01-2000");
                    assertThat(properties.getProviders().getTcmb().getEndDate())
                            .isEqualTo("01-01-2999");
                });
    }
}
