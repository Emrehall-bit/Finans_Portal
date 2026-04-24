package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderOrchestrationServiceTest {

    @Test
    void callsOnlyProvidersThatSupportRequest() {
        TestProvider supported = new TestProvider(DataSource.EVDS, true, List.of(quote("USDTRY")));
        TestProvider unsupported = new TestProvider(DataSource.BINANCE, false, List.of(quote("BTCUSDT")));
        ProviderOrchestrationService service = new ProviderOrchestrationService(List.of(supported, unsupported));

        var results = service.fetchQuoteResults(ProviderFetchRequest.forSource(DataSource.EVDS));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().source()).isEqualTo(DataSource.EVDS);
        assertThat(supported.fetchCount).isEqualTo(1);
        assertThat(unsupported.fetchCount).isZero();
    }

    @Test
    void continuesWhenOneProviderFails() {
        TestProvider failing = new TestProvider(DataSource.EVDS, true, null);
        failing.failure = new IllegalStateException("EVDS down");
        TestProvider successful = new TestProvider(DataSource.BINANCE, true, List.of(quote("BTCUSDT")));
        ProviderOrchestrationService service = new ProviderOrchestrationService(List.of(failing, successful));

        var results = service.fetchQuoteResults(ProviderFetchRequest.all());

        assertThat(results).hasSize(2);
        assertThat(results).anySatisfy(result -> {
            assertThat(result.source()).isEqualTo(DataSource.EVDS);
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("EVDS down");
        });
        assertThat(results).anySatisfy(result -> {
            assertThat(result.source()).isEqualTo(DataSource.BINANCE);
            assertThat(result.success()).isTrue();
            assertThat(result.quoteCount()).isEqualTo(1);
        });
    }

    @Test
    void treatsNullProviderResponseAsEmptySuccessfulResult() {
        TestProvider provider = new TestProvider(DataSource.EVDS, true, null);
        ProviderOrchestrationService service = new ProviderOrchestrationService(List.of(provider));

        var results = service.fetchQuoteResults(ProviderFetchRequest.all());

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.success()).isTrue();
            assertThat(result.quotes()).isEmpty();
            assertThat(result.quoteCount()).isZero();
        });
    }

    private static MarketQuote quote(String symbol) {
        Instant now = Instant.now();
        return new MarketQuote(
                symbol,
                symbol,
                InstrumentType.FX,
                BigDecimal.ONE,
                null,
                "TRY",
                DataSource.EVDS,
                now,
                now
        );
    }

    private static class TestProvider implements MarketDataProvider {

        private final DataSource source;
        private final boolean supports;
        private final List<MarketQuote> quotes;
        private RuntimeException failure;
        private int fetchCount;

        private TestProvider(DataSource source, boolean supports, List<MarketQuote> quotes) {
            this.source = source;
            this.supports = supports;
            this.quotes = quotes;
        }

        @Override
        public DataSource source() {
            return source;
        }

        @Override
        public boolean supports(ProviderFetchRequest request) {
            return supports;
        }

        @Override
        public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
            fetchCount++;
            if (failure != null) {
                throw failure;
            }
            return quotes;
        }
    }
}
