package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillProperties;
import com.emrehalli.financeportal.market.service.MarketHistoryService;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketHistoryController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class, MarketApiMapper.class})
class MarketHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketHistoryService marketHistoryService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @MockBean
    private Clock clock;

    @MockBean
    private MarketHistoryBackfillProperties backfillProperties;

    @MockBean
    private AppMessageSource appMessageSource;

    @BeforeEach
    void setUpClock() {
        mockClock(LocalDate.of(2026, 5, 4));
        when(backfillProperties.getRequiredHistoryPointCount()).thenReturn(50);
    }

    @Test
    void getHistoryFiltersBySourceWhenProvided() throws Exception {
        when(marketHistoryService.getHistory(
                "USDTRY",
                DataSource.EVDS,
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(historyRecord()));

        mockMvc.perform(get("/api/v1/markets/USDTRY/history")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .param("source", "EVDS")
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.pointCount").value(1));
    }

    @Test
    void getHistoryAllowsGuestAccess() throws Exception {
        when(marketHistoryService.getHistory(
                "USDTRY",
                DataSource.EVDS,
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(historyRecord()));

        mockMvc.perform(get("/api/v1/markets/USDTRY/history")
                        .param("source", "EVDS")
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));
    }

    @Test
    void getHistoryKeepsExistingBehaviorWhenSourceIsMissing() throws Exception {
        when(marketHistoryService.getHistory(
                "USDTRY",
                null,
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(historyRecord()));

        mockMvc.perform(get("/api/v1/markets/USDTRY/history")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));

        verify(marketHistoryService).getHistory(
                eq("USDTRY"),
                isNull(),
                eq(LocalDate.of(2025, 4, 24)),
                eq(LocalDate.of(2026, 4, 24))
        );
    }

    @Test
    void getHistorySupportsAliasPathWithFromToParams() throws Exception {
        when(marketHistoryService.getHistory(
                "TCMBFAIZ",
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 5, 7)
        )).thenReturn(List.of(new MarketHistoryRecord(
                "TCMBFAIZ",
                "TCMB Politika Faizi (%)",
                InstrumentType.MACRO_INDICATOR,
                DataSource.EVDS,
                LocalDate.of(2026, 4, 1),
                new BigDecimal("46.000000"),
                "TRY"
        )));

        mockMvc.perform(get("/api/v1/markets/history/TCMBFAIZ")
                        .param("from", "2024-01-01")
                        .param("to", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));

        verify(marketHistoryService).getHistory(
                eq("TCMBFAIZ"),
                isNull(),
                eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2026, 5, 7))
        );
    }

    @Test
    void getHistoryResolvesRangeWhenProvided() throws Exception {
        mockClock(LocalDate.of(2026, 5, 4));
        when(marketHistoryService.getHistory(
                "USDTRY",
                null,
                LocalDate.of(2026, 4, 4),
                LocalDate.of(2026, 5, 4)
        )).thenReturn(List.of(historyRecord()));

        mockMvc.perform(get("/api/v1/markets/USDTRY/history")
                        .param("range", "1m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));
    }

    @Test
    void getHistoryUsesDefaultOneMonthRangeWhenParamsAreMissing() throws Exception {
        mockClock(LocalDate.of(2026, 5, 4));
        when(marketHistoryService.getHistory(
                "USDTRY",
                null,
                LocalDate.of(2026, 4, 4),
                LocalDate.of(2026, 5, 4)
        )).thenReturn(List.of(historyRecord()));

        mockMvc.perform(get("/api/v1/markets/USDTRY/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));
    }

    @Test
    void getHistoryResolvesOneYearRangeWhenProvided() throws Exception {
        mockClock(LocalDate.of(2026, 5, 4));
        when(marketHistoryService.getHistory(
                "THYAO",
                null,
                LocalDate.of(2025, 5, 4),
                LocalDate.of(2026, 5, 4)
        )).thenReturn(List.of(new MarketHistoryRecord(
                "THYAO",
                "Turk Hava Yollari",
                InstrumentType.STOCK,
                DataSource.BIST,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("320.400000"),
                "TRY"
        )));

        mockMvc.perform(get("/api/v1/markets/THYAO/history")
                        .param("range", "1y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));

        verify(marketHistoryService).getHistory(
                eq("THYAO"),
                isNull(),
                eq(LocalDate.of(2025, 5, 4)),
                eq(LocalDate.of(2026, 5, 4))
        );
    }

    @Test
    void getHistoryReturnsBadRequestWhenRangeIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/markets/USDTRY/history")
                        .param("range", "42q"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistoryReturnsBadRequestWhenSourceIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/markets/USDTRY/history")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .param("source", "INVALID")
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistorySupportsBistSymbols() throws Exception {
        when(marketHistoryService.getHistory(
                "THYAO",
                DataSource.BIST,
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryRecord(
                "THYAO",
                "Turk Hava Yollari",
                InstrumentType.STOCK,
                DataSource.BIST,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("320.400000"),
                "TRY"
        )));

        mockMvc.perform(get("/api/v1/markets/THYAO/history")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .param("source", "BIST")
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));
    }

    @Test
    void getHistorySupportsBistWithoutSerializationFailure() throws Exception {
        when(marketHistoryService.getHistory(
                "THYAO",
                DataSource.BIST,
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryRecord(
                "THYAO",
                "Turk Hava Yollari",
                InstrumentType.STOCK,
                DataSource.BIST,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("320.400000"),
                "TRY"
        )));

        mockMvc.perform(get("/api/v1/markets/THYAO/history")
                        .param("source", "BIST")
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));
    }

    @Test
    void getHistoryDebugReturnsPointSummary() throws Exception {
        when(marketHistoryService.getHistory(
                "THYAO",
                DataSource.BIST,
                LocalDate.of(2025, 5, 4),
                LocalDate.of(2026, 5, 4)
        )).thenReturn(List.of(
                new MarketHistoryRecord("THYAO", "THYAO", InstrumentType.STOCK, DataSource.BIST, LocalDate.of(2026, 4, 24), new BigDecimal("320.400000"), "TRY"),
                new MarketHistoryRecord("THYAO", "THYAO", InstrumentType.STOCK, DataSource.BIST, LocalDate.of(2026, 5, 4), new BigDecimal("328.100000"), "TRY")
        ));

        mockMvc.perform(get("/api/v1/markets/THYAO/history/debug")
                        .param("source", "BIST")
                        .param("range", "1y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("THYAO"))
                .andExpect(jsonPath("$.requestedRange").value("1y"))
                .andExpect(jsonPath("$.pointCount").value(2))
                .andExpect(jsonPath("$.minDate").value("2026-04-24"))
                .andExpect(jsonPath("$.maxDate").value("2026-05-04"))
                .andExpect(jsonPath("$.distinctPriceCount").value(2))
                .andExpect(jsonPath("$.source").value("BIST"));
    }

    @ParameterizedTest
    @CsvSource({
            "ETHUSDT,ETH / USDT",
            "BNBUSDT,BNB / USDT",
            "SOLUSDT,SOL / USDT"
    })
    void getHistorySupportsAdditionalBinanceSymbols(String symbol, String displayName) throws Exception {
        when(marketHistoryService.getHistory(
                symbol,
                DataSource.BINANCE,
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(cryptoHistoryRecord(symbol, displayName)));

                mockMvc.perform(get("/api/v1/markets/{symbol}/history", symbol)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .param("source", "BINANCE")
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("INSUFFICIENT_HISTORY"));
    }

    private static MarketHistoryRecord historyRecord() {
        return new MarketHistoryRecord(
                "USDTRY",
                "USD / TRY",
                InstrumentType.FX,
                DataSource.EVDS,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("38.123400"),
                "TRY"
        );
    }

    private static MarketHistoryRecord cryptoHistoryRecord(String symbol, String displayName) {
        return new MarketHistoryRecord(
                symbol,
                displayName,
                InstrumentType.CRYPTO,
                DataSource.BINANCE,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("123.450000"),
                "USDT"
        );
    }

    private void mockClock(LocalDate date) {
        when(clock.instant()).thenReturn(date.atStartOfDay().toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }
}
