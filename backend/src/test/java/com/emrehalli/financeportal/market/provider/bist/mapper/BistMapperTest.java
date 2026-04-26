package com.emrehalli.financeportal.market.provider.bist.mapper;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.bist.dto.BistQuoteResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BistMapperTest {

    private final BistMapper mapper = new BistMapper();

    @Test
    void mapsQuoteAndHistoryRecordsFromBistPayload() {
        BistQuoteResponse response = new BistQuoteResponse(
                "THYAO.IS",
                "Turk Hava Yollari",
                null,
                new BigDecimal("320.40"),
                new BigDecimal("1.25"),
                1777033800L
        );

        var quotes = mapper.toMarketQuotes(List.of(response));
        var historyRecords = mapper.toHistoryRecords(List.of(response));

        assertThat(quotes).singleElement().satisfies(quote -> {
            assertThat(quote.symbol()).isEqualTo("THYAO");
            assertThat(quote.displayName()).isEqualTo("Turk Hava Yollari");
            assertThat(quote.instrumentType()).isEqualTo(InstrumentType.STOCK);
            assertThat(quote.source()).isEqualTo(DataSource.BIST);
            assertThat(quote.currency()).isEqualTo("TRY");
            assertThat(quote.price()).hasToString("320.40");
            assertThat(quote.changeRate()).hasToString("1.25");
            assertThat(quote.priceTime()).isEqualTo(Instant.ofEpochSecond(1777033800L));
        });
        assertThat(historyRecords).singleElement().satisfies(record -> {
            assertThat(record.symbol()).isEqualTo("THYAO");
            assertThat(record.instrumentType()).isEqualTo(InstrumentType.STOCK);
            assertThat(record.source()).isEqualTo(DataSource.BIST);
            assertThat(record.priceDate()).isEqualTo(LocalDate.of(2026, 4, 24));
            assertThat(record.closePrice()).hasToString("320.40");
        });
    }

    @Test
    void fallsBackToPriceWhenCloseIsMissing() {
        var quotes = mapper.toMarketQuotes(List.of(
                new BistQuoteResponse("ASELS.IS", null, "Aselsan", new BigDecimal("145.20"), null, null)
        ));

        assertThat(quotes).singleElement().satisfies(quote -> {
            assertThat(quote.symbol()).isEqualTo("ASELS");
            assertThat(quote.displayName()).isEqualTo("Aselsan");
            assertThat(quote.price()).hasToString("145.20");
        });
    }
}
