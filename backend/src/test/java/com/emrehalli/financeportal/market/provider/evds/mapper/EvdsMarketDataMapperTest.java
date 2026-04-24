package com.emrehalli.financeportal.market.provider.evds.mapper;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.evds.EvdsMarketDataMapper;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsItem;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsMarketDataMapperTest {

    private final EvdsMarketDataMapper mapper = new EvdsMarketDataMapper();
    private final EvdsProperties.SeriesConfig usdTry = usdSeries();

    @Test
    void selectsLatestValidRecordByDateNotResponseOrder() {
        EvdsResponse response = new EvdsResponse(List.of(
                item("23-04-2026", "38,75"),
                item("21-04-2026", "37.10"),
                item("22-04-2026", "38.20")
        ));

        var quotes = mapper.toMarketQuotes(response, List.of(usdTry));

        assertThat(quotes).singleElement().satisfies(quote -> {
            assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("38.75"));
            assertThat(quote.priceTime()).isEqualTo(LocalDate.of(2026, 4, 23)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC));
        });
    }

    @Test
    void skipsBlankInvalidMissingValueAndUnparseableDateRecords() {
        EvdsResponse response = new EvdsResponse(List.of(
                item("not-a-date", "41.00"),
                item("22-04-2026", ""),
                item("23-04-2026", null),
                itemWithoutProviderColumn("24-04-2026"),
                item("21-04-2026", "39,10")
        ));

        var quotes = mapper.toMarketQuotes(response, List.of(usdTry));

        assertThat(quotes).singleElement().satisfies(quote ->
                assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("39.10"))
        );
    }

    @Test
    void returnsNoQuoteWhenNoValidRecordExists() {
        EvdsResponse response = new EvdsResponse(List.of(
                item("not-a-date", "41.00"),
                item("22-04-2026", "not-a-number")
        ));

        assertThat(mapper.toMarketQuotes(response, List.of(usdTry))).isEmpty();
    }

    @Test
    void createsHistoryRecordForEachValidDate() {
        EvdsResponse response = new EvdsResponse(List.of(
                item("21-04-2026", "37.10"),
                item("22-04-2026", "38.20"),
                item("23-04-2026", "")
        ));

        var historyRecords = mapper.toHistoryRecords(response, List.of(usdTry));

        assertThat(historyRecords).hasSize(2);
        assertThat(historyRecords).extracting(record -> record.priceDate().toString())
                .containsExactly("2026-04-21", "2026-04-22");
    }

    private static EvdsItem item(String date, String value) {
        EvdsItem item = new EvdsItem();
        item.put("Tarih", date);
        item.put("TP_DK_USD_A", value);
        return item;
    }

    private static EvdsItem itemWithoutProviderColumn(String date) {
        EvdsItem item = new EvdsItem();
        item.put("Tarih", date);
        item.put("OTHER", "42.00");
        return item;
    }

    private static EvdsProperties.SeriesConfig usdSeries() {
        EvdsProperties.SeriesConfig config = new EvdsProperties.SeriesConfig();
        config.setEvdsKey("TP_DK_USD_A");
        config.setApiCode("TP.DK.USD.A");
        config.setSymbol("USDTRY");
        config.setName("USD/TRY");
        config.setInstrumentType(InstrumentType.FX);
        config.setCurrency("TRY");
        return config;
    }
}
