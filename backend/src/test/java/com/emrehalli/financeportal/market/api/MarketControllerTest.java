package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.exception.GlobalExceptionHandler;
import com.emrehalli.financeportal.config.ObservabilityFilterConfig;
import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.market.exception.MarketDataNotFoundException;
import com.emrehalli.financeportal.market.exception.MarketExceptionHandler;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
@Import({
        SecurityConfig.class,
        KeycloakJwtRoleConverter.class,
        MarketApiMapper.class,
        ObservabilityFilterConfig.class,
        MarketExceptionHandler.class,
        GlobalExceptionHandler.class
})
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketQueryService marketQueryService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @Test
    void getAllQuotesIncludesTefasFunds() throws Exception {
        when(marketQueryService.getAllQuotes()).thenReturn(List.of(tefasQuote()));

        mockMvc.perform(get("/api/v1/markets")
                        .header("X-Request-Id", "market-request-123")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "market-request-123"))
                .andExpect(jsonPath("$[0].symbol").value("AFT"))
                .andExpect(jsonPath("$[0].instrumentType").value("FUND"))
                .andExpect(jsonPath("$[0].source").value("TEFAS"));
    }

    @Test
    void getBySymbolReturnsTefasFund() throws Exception {
        when(marketQueryService.getQuoteBySymbol("AFT")).thenReturn(tefasQuote());

        mockMvc.perform(get("/api/v1/markets/AFT")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AFT"))
                .andExpect(jsonPath("$.displayName").value("AK PORTFOY ALTIN FONU"))
                .andExpect(jsonPath("$.source").value("TEFAS"));
    }

    @Test
    void getBySymbolReturnsBistQuote() throws Exception {
        when(marketQueryService.getQuoteBySymbol("THYAO")).thenReturn(bistQuote());

        mockMvc.perform(get("/api/v1/markets/THYAO")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.symbol").value("THYAO"))
                .andExpect(jsonPath("$.displayName").value("Turk Hava Yollari"))
                .andExpect(jsonPath("$.instrumentType").value("STOCK"))
                .andExpect(jsonPath("$.source").value("BIST"));
    }

    @Test
    void marketErrorsIncludeRequestId() throws Exception {
        when(marketQueryService.getQuoteBySymbol("UNKNOWN"))
                .thenThrow(new MarketDataNotFoundException("Market quote not found for symbol: UNKNOWN"));

        mockMvc.perform(get("/api/v1/markets/UNKNOWN")
                        .header("X-Request-Id", "market-error-req")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "market-error-req"))
                .andExpect(jsonPath("$.requestId").value("market-error-req"));
    }

    private MarketQuote tefasQuote() {
        Instant now = Instant.parse("2026-04-24T12:00:00Z");
        return new MarketQuote(
                "AFT",
                "AK PORTFOY ALTIN FONU",
                InstrumentType.FUND,
                new BigDecimal("12.345678"),
                new BigDecimal("1.2345"),
                "TRY",
                DataSource.TEFAS,
                now,
                now
        );
    }

    private MarketQuote bistQuote() {
        Instant now = Instant.parse("2026-04-24T12:00:00Z");
        return new MarketQuote(
                "THYAO",
                "Turk Hava Yollari",
                InstrumentType.STOCK,
                new BigDecimal("320.40"),
                new BigDecimal("1.25"),
                "TRY",
                DataSource.BIST,
                now,
                now
        );
    }
}
