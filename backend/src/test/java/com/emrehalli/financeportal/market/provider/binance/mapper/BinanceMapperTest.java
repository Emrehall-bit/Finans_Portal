package com.emrehalli.financeportal.market.provider.binance.mapper;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceKlineResponse;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerResponse;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BinanceMapperTest {

    private final BinanceMapper mapper = new BinanceMapper();

    @Test
    void mapsValidTickerFieldsToMarketQuote() {
        List<?> quotes = mapper.toMarketQuotes(List.of(
                new BinanceTickerResponse("BTCUSDT", "93500.10", "4.20", 1713900000000L)
        ));

        assertThat(quotes).singleElement().satisfies(item -> {
            var quote = (com.emrehalli.financeportal.market.domain.MarketQuote) item;
            assertThat(quote.symbol()).isEqualTo("BTCUSDT");
            assertThat(quote.instrumentType()).isEqualTo(InstrumentType.CRYPTO);
            assertThat(quote.source()).isEqualTo(DataSource.BINANCE);
            assertThat(quote.currency()).isEqualTo("USDT");
            assertThat(quote.priceTime()).isEqualTo(Instant.ofEpochMilli(1713900000000L));
            assertThat(quote.changeRate()).hasToString("4.20");
            assertThat(quote.fetchedAt()).isNotNull();
        });
    }

    @Test
    void skipsTickersWithMissingOrInvalidPrice() {
        var quotes = mapper.toMarketQuotes(List.of(
                new BinanceTickerResponse("BTCUSDT", null, "4.20", 1713900000000L),
                new BinanceTickerResponse("ETHUSDT", "abc", "1.50", 1713900000000L),
                new BinanceTickerResponse("BNBUSDT", "601.10", "0.50", 1713900000000L)
        ));

        assertThat(quotes).singleElement().satisfies(quote -> {
            assertThat(quote.symbol()).isEqualTo("BNBUSDT");
            assertThat(quote.price()).hasToString("601.10");
        });
    }

    @Test
    void mapsValidKlinesToMarketHistoryRecords() {
        List<MarketHistoryRecord> history = mapper.toHistoryRecords("BTCUSDT", List.of(
                new BinanceKlineResponse(1713830400000L, "92000.00", "94000.00", "91000.00", "93500.10", "1000.00", 1713916799999L)
        ));

        assertThat(history).singleElement().satisfies(record -> {
            assertThat(record.symbol()).isEqualTo("BTCUSDT");
            assertThat(record.displayName()).isEqualTo("BTC / USDT");
            assertThat(record.instrumentType()).isEqualTo(InstrumentType.CRYPTO);
            assertThat(record.source()).isEqualTo(DataSource.BINANCE);
            assertThat(record.priceDate()).isEqualTo(LocalDate.of(2024, 4, 23));
            assertThat(record.closePrice()).hasToString("93500.10");
            assertThat(record.currency()).isEqualTo("USDT");
        });
    }
}
