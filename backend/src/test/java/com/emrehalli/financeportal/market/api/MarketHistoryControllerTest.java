package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketHistoryService;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
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
                .andExpect(jsonPath("$[0].symbol").value("USDTRY"))
                .andExpect(jsonPath("$[0].source").value("EVDS"));
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
                .andExpect(jsonPath("$[0].symbol").value("USDTRY"));
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
                .andExpect(jsonPath("$[0].symbol").value("USDTRY"));

        verify(marketHistoryService).getHistory(
                eq("USDTRY"),
                isNull(),
                eq(LocalDate.of(2025, 4, 24)),
                eq(LocalDate.of(2026, 4, 24))
        );
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
    void getHistorySupportsTefasFunds() throws Exception {
        when(marketHistoryService.getHistory(
                "AFT",
                DataSource.TEFAS,
                LocalDate.of(2025, 4, 24),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(new MarketHistoryRecord(
                "AFT",
                "AK PORTFOY ALTIN FONU",
                InstrumentType.FUND,
                DataSource.TEFAS,
                LocalDate.of(2026, 4, 24),
                new BigDecimal("12.345678"),
                "TRY"
        )));

        mockMvc.perform(get("/api/v1/markets/AFT/history")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .param("source", "TEFAS")
                        .param("startDate", "2025-04-24")
                        .param("endDate", "2026-04-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AFT"))
                .andExpect(jsonPath("$[0].source").value("TEFAS"))
                .andExpect(jsonPath("$[0].currency").value("TRY"));
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
                .andExpect(jsonPath("$[0].symbol").value(symbol))
                .andExpect(jsonPath("$[0].source").value("BINANCE"))
                .andExpect(jsonPath("$[0].currency").value("USDT"));
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
}
