package com.emrehalli.financeportal.market.cache;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCacheTtlPolicyTest {

    private final MarketCacheTtlPolicy policy = policy();

    @Test
    void resolvesSourceSpecificTtls() {
        assertThat(policy.ttlFor(DataSource.EVDS)).isEqualTo(Duration.ofHours(6));
        assertThat(policy.ttlFor(DataSource.BINANCE)).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.ttlFor(DataSource.BIST)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.ttlFor(DataSource.UNKNOWN)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void keepsGlobalAllQuotesTtlAsDefault() {
        assertThat(policy.allQuotesTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.symbolQuoteTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    private MarketCacheTtlPolicy policy() {
        MarketCacheProperties properties = new MarketCacheProperties();
        properties.setTtl(java.util.Map.of(
                "EVDS", Duration.ofHours(6),
                "BINANCE", Duration.ofMinutes(2),
                "BIST", Duration.ofMinutes(15),
                "ALL", Duration.ofMinutes(30)
        ));
        return new MarketCacheTtlPolicy(properties);
    }
}
