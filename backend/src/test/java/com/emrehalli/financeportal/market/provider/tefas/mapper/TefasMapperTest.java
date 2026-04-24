package com.emrehalli.financeportal.market.provider.tefas.mapper;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.tefas.dto.TefasFundResponse;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TefasMapperTest {

    private final TefasMapper mapper = new TefasMapper();

    @Test
    void mapsFundResponseToMarketQuote() {
        var quotes = mapper.toMarketQuotes(List.of(
                new TefasFundResponse("AFT", "AK PORTFOY ALTIN FONU", "12,345678", "1,2345", LocalDate.of(2026, 4, 24))
        ));

        assertThat(quotes).singleElement().satisfies(quote -> {
            assertThat(quote.symbol()).isEqualTo("AFT");
            assertThat(quote.displayName()).isEqualTo("AK PORTFOY ALTIN FONU");
            assertThat(quote.instrumentType()).isEqualTo(InstrumentType.FUND);
            assertThat(quote.price()).hasToString("12.345678");
            assertThat(quote.changeRate()).hasToString("1.2345");
            assertThat(quote.currency()).isEqualTo("TRY");
            assertThat(quote.source()).isEqualTo(DataSource.TEFAS);
            assertThat(quote.priceTime()).isNotNull();
        });
    }

    @Test
    void mapsFundResponseToHistoryRecord() {
        List<MarketHistoryRecord> history = mapper.toHistoryRecords(List.of(
                new TefasFundResponse("AFT", "AK PORTFOY ALTIN FONU", "12,345678", "1,2345", LocalDate.of(2026, 4, 24))
        ));

        assertThat(history).singleElement().satisfies(record -> {
            assertThat(record.symbol()).isEqualTo("AFT");
            assertThat(record.displayName()).isEqualTo("AK PORTFOY ALTIN FONU");
            assertThat(record.instrumentType()).isEqualTo(InstrumentType.FUND);
            assertThat(record.source()).isEqualTo(DataSource.TEFAS);
            assertThat(record.priceDate()).isEqualTo(LocalDate.of(2026, 4, 24));
            assertThat(record.closePrice()).hasToString("12.345678");
            assertThat(record.currency()).isEqualTo("TRY");
        });
    }
}
