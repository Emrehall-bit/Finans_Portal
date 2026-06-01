package com.emrehalli.financeportal.technicalanalysis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO nesting (ComparisonPointDto/SeriesDto/TechnicalAnalysisPointDto/IndicatorValueDto -> nested record)
 * sonrası serialize edilen JSON alan isimlerinin/yapısının önceki standalone DTO'larla birebir aynı
 * kaldığını kilitler. Wire format değişmediğinin kanıtıdır.
 */
class TechnicalAnalysisResponseJsonShapeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void technicalAnalysisResponse_json_field_names_unchanged_after_dto_nesting() {
        TechnicalAnalysisResponse response = new TechnicalAnalysisResponse(
                "BTCUSDT",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1),
                new BigDecimal("100.00"),
                "AVAILABLE",
                null,
                "UPTREND",
                List.of("PRICE_ABOVE_SMA20", "RSI_NEUTRAL"),
                List.of(new TechnicalAnalysisResponse.IndicatorValue("SMA7", new BigDecimal("99.5"))),
                List.of(new TechnicalAnalysisResponse.Point(
                        LocalDate.of(2026, 3, 1),
                        new BigDecimal("100.00"),
                        new BigDecimal("99.5"),
                        new BigDecimal("98.0"),
                        new BigDecimal("97.0"),
                        new BigDecimal("55.0")
                )),
                new TechnicalAnalysisResponse.TrendContextResponse(
                        "UPTREND", "UPTREND", "UPTREND", "3M", 63, false
                )
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "symbol", "from", "to", "latestPrice", "analysisStatus", "message",
                "trendDirection", "signals", "indicatorValues", "points", "trendContext");
        assertThat(json.get("signals").isArray()).isTrue();
        assertThat(fieldNames(json.get("indicatorValues").get(0)))
                .containsExactlyInAnyOrder("indicator", "value");
        assertThat(fieldNames(json.get("points").get(0)))
                .containsExactlyInAnyOrder("date", "close", "sma7", "sma20", "sma50", "rsi14");
    }

    @Test
    void comparisonResponse_json_field_names_unchanged_after_dto_nesting() {
        ComparisonResponse response = new ComparisonResponse(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1),
                List.of(new ComparisonResponse.Series(
                        "BTCUSDT",
                        List.of(new ComparisonResponse.Point(
                                LocalDate.of(2026, 3, 1),
                                new BigDecimal("100.00"),
                                new BigDecimal("105.00")
                        ))
                ))
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("from", "to", "series");
        assertThat(fieldNames(json.get("series").get(0)))
                .containsExactlyInAnyOrder("symbol", "points");
        assertThat(fieldNames(json.get("series").get(0).get("points").get(0)))
                .containsExactlyInAnyOrder("date", "close", "normalizedValue");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
