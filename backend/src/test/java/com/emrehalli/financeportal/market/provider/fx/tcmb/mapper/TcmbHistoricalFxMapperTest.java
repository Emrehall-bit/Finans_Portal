package com.emrehalli.financeportal.market.provider.fx.tcmb.mapper;

import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbFxSeriesDefinitions;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbHistoricalFxValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TcmbHistoricalFxMapperTest {

    private final TcmbHistoricalFxMapper mapper = new TcmbHistoricalFxMapper();

    @Test
    void mapRowsParsesBigDecimalValue() {
        List<Map<String, Object>> rows = List.of(
                Map.of(
                        "Tarih", "01-01-2024",
                        "TP_DK_USD_S_YTL", "29.5000"
                )
        );

        List<TcmbHistoricalFxValue> values = mapper.mapRows(rows, List.of(TcmbFxSeriesDefinitions.USDTRY_SELL));

        assertThat(values).hasSize(1);
        assertThat(values.getFirst().instrumentCode()).isEqualTo("TCMB:USD:SELL");
        assertThat(values.getFirst().seriesCode()).isEqualTo("TP.DK.USD.S.YTL");
        assertThat(values.getFirst().priceDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(values.getFirst().priceValue()).isEqualByComparingTo(new BigDecimal("29.5000"));
    }

    @Test
    void mapRowsSkipsNullAndBlankAndInvalidValues() {
        List<Map<String, Object>> rows = List.of(
                Map.of(
                        "Tarih", "01-01-2024",
                        "TP_DK_USD_S_YTL", ""
                ),
                Map.of(
                        "Tarih", "02-01-2024",
                        "TP_DK_USD_S_YTL", "-"
                ),
                Map.of(
                        "Tarih", "03-01-2024",
                        "TP_DK_USD_S_YTL", "."
                )
        );

        List<TcmbHistoricalFxValue> values = mapper.mapRows(rows, List.of(TcmbFxSeriesDefinitions.USDTRY_SELL));

        assertThat(values).isEmpty();
    }

    @Test
    void mapRowsSkipsNullObjectValues() {
        List<Map<String, Object>> rows = List.of(
                Map.of("Tarih", "01-01-2024"),
                Map.of(
                        "Tarih", "02-01-2024",
                        "TP_DK_USD_S_YTL", "29,7500"
                )
        );

        List<TcmbHistoricalFxValue> values = mapper.mapRows(rows, List.of(TcmbFxSeriesDefinitions.USDTRY_SELL));

        assertThat(values).hasSize(1);
        assertThat(values.getFirst().priceValue()).isEqualByComparingTo(new BigDecimal("29.7500"));
    }
}




