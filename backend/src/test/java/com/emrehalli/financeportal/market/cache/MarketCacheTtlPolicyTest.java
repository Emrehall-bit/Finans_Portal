package com.emrehalli.financeportal.market.cache;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCacheTtlPolicyTest {

    private final MarketCacheTtlPolicy policy = new MarketCacheTtlPolicy();

    @Test
    void resolvesSourceSpecificTtls() {
        assertThat(policy.ttlFor(DataSource.EVDS)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.ttlFor(DataSource.BINANCE)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.ttlFor(DataSource.TEFAS)).isEqualTo(Duration.ofDays(1));
        assertThat(policy.ttlFor(DataSource.BIST)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.ttlFor(DataSource.UNKNOWN)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void keepsGlobalAllQuotesTtlAsDefault() {
        assertThat(policy.allQuotesTtl()).isEqualTo(Duration.ofMinutes(10));
    }
}
