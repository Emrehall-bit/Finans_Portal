package com.emrehalli.financeportal.market.provider.macro.tcmb;

import com.emrehalli.financeportal.market.domain.enums.MacroValueType;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroObservationParseResult;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroSeriesField;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TcmbMacroProviderTest {

    private static final List<MacroSeriesField> CPI_FIELDS = List.of(
            new MacroSeriesField("TP_TUKFIY2025_GENEL-1", MacroValueType.MONTHLY_CHANGE, "CPI_TR"),
            new MacroSeriesField("TP_TUKFIY2025_GENEL-3", MacroValueType.YEARLY_CHANGE,  "CPI_TR")
    );

    private static final List<MacroSeriesField> PPI_FIELDS = List.of(
            new MacroSeriesField("TP_TUFE1YI_T1-1", MacroValueType.MONTHLY_CHANGE, "PPI_TR"),
            new MacroSeriesField("TP_TUFE1YI_T1-3", MacroValueType.YEARLY_CHANGE,  "PPI_TR")
    );

    private static final List<MacroSeriesField> POLICY_RATE_FIELDS = List.of(
            new MacroSeriesField("TP_BISPOLFAIZ_TUR", MacroValueType.POLICY_RATE, "POLICY_RATE_TR")
    );

    private static final List<MacroSeriesField> LABOR_MARKET_FIELDS = List.of(
            new MacroSeriesField("TP_YISGUCU2_G8", MacroValueType.UNEMPLOYMENT_RATE,              "UNEMPLOYMENT_TR"),
            new MacroSeriesField("TP_YISGUCU2_G6", MacroValueType.LABOR_FORCE_PARTICIPATION_RATE, "LABOR_FORCE_PARTICIPATION_TR")
    );

    private TcmbMacroProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TcmbMacroProvider(
                mock(RestClient.class),
                new ObjectMapper(),
                "https://evds3.tcmb.gov.tr/igmevdsms-dis/fe"
        );
    }

    @Test
    void parseItems_cpi_parsesMonthlyAndYearlyChange() {
        String body = """
                {
                  "items": [
                    {
                      "Tarih": "2026-04",
                      "TP_TUKFIY2025_GENEL-1": "4.18",
                      "TP_TUKFIY2025_GENEL-3": "32.37",
                      "UNIXTIME": {"$numberLong": "1774990800"}
                    }
                  ]
                }
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, CPI_FIELDS);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.indicatorCode().equals("CPI_TR"));

        MacroObservationParseResult monthly = results.stream()
                .filter(r -> r.valueType() == MacroValueType.MONTHLY_CHANGE)
                .findFirst().orElseThrow();
        assertThat(monthly.value()).isEqualByComparingTo(new BigDecimal("4.18"));
        assertThat(monthly.observationDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(monthly.periodLabel()).isEqualTo("2026-04");

        assertThat(results.stream().filter(r -> r.valueType() == MacroValueType.YEARLY_CHANGE)
                .findFirst().orElseThrow().value()).isEqualByComparingTo(new BigDecimal("32.37"));
    }

    @Test
    void parseItems_ppi_parsesCorrectIndicatorCode() {
        String body = """
                {
                  "items": [
                    {"Tarih": "2026-04", "TP_TUFE1YI_T1-1": "3.17", "TP_TUFE1YI_T1-3": "28.59"}
                  ]
                }
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, PPI_FIELDS);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.indicatorCode().equals("PPI_TR"));
        assertThat(results.stream().filter(r -> r.valueType() == MacroValueType.MONTHLY_CHANGE)
                .findFirst().orElseThrow().value()).isEqualByComparingTo(new BigDecimal("3.17"));
    }

    @Test
    void parseItems_policyRate_parsesSingleField() {
        String body = """
                {
                  "items": [
                    {"Tarih": "2026-03", "TP_BISPOLFAIZ_TUR": "37.00"},
                    {"Tarih": "2026-02", "TP_BISPOLFAIZ_TUR": "42.50"}
                  ]
                }
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, POLICY_RATE_FIELDS);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.indicatorCode().equals("POLICY_RATE_TR"));
        assertThat(results).allMatch(r -> r.valueType() == MacroValueType.POLICY_RATE);
    }

    @Test
    void parseItems_laborMarket_producesTwoIndicatorsFromOneItem() {
        String body = """
                {
                  "items": [
                    {"Tarih": "2026-03", "TP_YISGUCU2_G8": "8.10", "TP_YISGUCU2_G6": "51.80"}
                  ]
                }
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, LABOR_MARKET_FIELDS);

        assertThat(results).hasSize(2);

        MacroObservationParseResult unemployment = results.stream()
                .filter(r -> r.valueType() == MacroValueType.UNEMPLOYMENT_RATE)
                .findFirst().orElseThrow();
        assertThat(unemployment.indicatorCode()).isEqualTo("UNEMPLOYMENT_TR");
        assertThat(unemployment.value()).isEqualByComparingTo(new BigDecimal("8.10"));

        MacroObservationParseResult laborForce = results.stream()
                .filter(r -> r.valueType() == MacroValueType.LABOR_FORCE_PARTICIPATION_RATE)
                .findFirst().orElseThrow();
        assertThat(laborForce.indicatorCode()).isEqualTo("LABOR_FORCE_PARTICIPATION_TR");
        assertThat(laborForce.value()).isEqualByComparingTo(new BigDecimal("51.80"));
    }

    @Test
    void parseItems_laborMarket_multipleRows() {
        String body = """
                {
                  "items": [
                    {"Tarih": "2026-02", "TP_YISGUCU2_G8": "8.30", "TP_YISGUCU2_G6": "51.50"},
                    {"Tarih": "2026-03", "TP_YISGUCU2_G8": "8.10", "TP_YISGUCU2_G6": "51.80"}
                  ]
                }
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, LABOR_MARKET_FIELDS);

        assertThat(results).hasSize(4);
        assertThat(results.stream().filter(r -> r.indicatorCode().equals("UNEMPLOYMENT_TR"))).hasSize(2);
        assertThat(results.stream().filter(r -> r.indicatorCode().equals("LABOR_FORCE_PARTICIPATION_TR"))).hasSize(2);
    }

    @Test
    void parseItems_multipleRows_cpi() {
        String body = """
                {
                  "items": [
                    {"Tarih": "2026-02", "TP_TUKFIY2025_GENEL-1": "1.10", "TP_TUKFIY2025_GENEL-3": "39.05"},
                    {"Tarih": "2026-03", "TP_TUKFIY2025_GENEL-1": "2.46", "TP_TUKFIY2025_GENEL-3": "38.10"}
                  ]
                }
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, CPI_FIELDS);

        assertThat(results).hasSize(4);
        assertThat(results.stream().map(MacroObservationParseResult::observationDate))
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void parseItems_skipsRowWithMissingDate() {
        String body = """
                {"items": [{"TP_TUKFIY2025_GENEL-1": "4.18", "TP_TUKFIY2025_GENEL-3": "32.37"}]}
                """;

        assertThat(provider.parseItems(body, CPI_FIELDS)).isEmpty();
    }

    @Test
    void parseItems_skipsUnparsableDecimalButKeepsValidField() {
        String body = """
                {"items": [{"Tarih": "2026-04", "TP_TUKFIY2025_GENEL-1": "N/A", "TP_TUKFIY2025_GENEL-3": "32.37"}]}
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, CPI_FIELDS);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().valueType()).isEqualTo(MacroValueType.YEARLY_CHANGE);
    }

    @Test
    void parseItems_skipsRowWithUnparsableDate() {
        String body = """
                {"items": [{"Tarih": "invalid-date", "TP_TUKFIY2025_GENEL-1": "4.18"}]}
                """;

        assertThat(provider.parseItems(body, CPI_FIELDS)).isEmpty();
    }

    @Test
    void parseItems_returnsEmptyWhenNoItemsArray() {
        assertThat(provider.parseItems("{\"otherField\": \"value\"}", CPI_FIELDS)).isEmpty();
    }

    @Test
    void parseItems_handlesCommaAsDecimalSeparator() {
        String body = """
                {"items": [{"Tarih": "2026-04", "TP_TUKFIY2025_GENEL-1": "4,18", "TP_TUKFIY2025_GENEL-3": "32,37"}]}
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, CPI_FIELDS);

        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(r -> r.valueType() == MacroValueType.MONTHLY_CHANGE)
                .findFirst().orElseThrow().value()).isEqualByComparingTo(new BigDecimal("4.18"));
    }

    @Test
    void parseItems_currentAccount_parsesNegativeValueWithThousandsSeparator() {
        List<MacroSeriesField> fields = List.of(
                new MacroSeriesField("TP_ODEAYRSUNUM6_Q1", MacroValueType.CURRENT_ACCOUNT_BALANCE, "CURRENT_ACCOUNT_TR")
        );
        String body = """
                {
                  "items": [
                    {"Tarih": "2013-09", "TP_ODEAYRSUNUM6_Q1": "-1,023.00"},
                    {"Tarih": "2014-01", "TP_ODEAYRSUNUM6_Q1": "2,540.75"},
                    {"Tarih": "2014-02", "TP_ODEAYRSUNUM6_Q1": "-10,234.56"}
                  ]
                }
                """;

        List<MacroObservationParseResult> results = provider.parseItems(body, fields);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).value()).isEqualByComparingTo(new BigDecimal("-1023.00"));
        assertThat(results.get(1).value()).isEqualByComparingTo(new BigDecimal("2540.75"));
        assertThat(results.get(2).value()).isEqualByComparingTo(new BigDecimal("-10234.56"));
        assertThat(results).allMatch(r -> r.indicatorCode().equals("CURRENT_ACCOUNT_TR"));
        assertThat(results).allMatch(r -> r.valueType() == MacroValueType.CURRENT_ACCOUNT_BALANCE);
    }
}



