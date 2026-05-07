package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.exception.GlobalExceptionHandler;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.config.ObservabilityFilterConfig;
import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MarketPriceStatus;
import com.emrehalli.financeportal.market.exception.MarketExceptionHandler;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.market.support.InstrumentTypeAliasResolver;
import com.emrehalli.financeportal.market.service.model.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
        ObservabilityFilterConfig.class,
        GlobalExceptionHandler.class,
        MarketExceptionHandler.class,
        MarketApiMapper.class,
        MarketApiResponseFactory.class,
        InstrumentTypeAliasResolver.class
})
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketQueryService marketQueryService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @MockBean
    private AppMessageSource appMessageSource;

    @BeforeEach
    void setUpMessages() {
        when(appMessageSource.get("market.dataFetched")).thenReturn("Market data fetched");
    }

    @Test
    void getMarketsReturnsAggregateQuotesFromReadPipeline() throws Exception {
        when(marketQueryService.getAllQuotes()).thenReturn(List.of(currencyQuote(), stockQuote()));

        mockMvc.perform(get("/api/v1/markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Market data fetched"))
                .andExpect(jsonPath("$.data[0].symbol").value("USDTRY"))
                .andExpect(jsonPath("$.data[1].symbol").value("AAPL"));
    }

    @Test
    void getMarketsSupportsTypeQueryParam() throws Exception {
        when(marketQueryService.getAllQuotes()).thenReturn(List.of(currencyQuote(), stockQuote(), macroQuote()));

        mockMvc.perform(get("/api/v1/markets").param("type", "MACRO_INDICATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].symbol").value("TCMBFAIZ"))
                .andExpect(jsonPath("$.data[0].instrumentType").value("MACRO_INDICATOR"));
    }

    @Test
    void getBySymbolReturnsSingleQuote() throws Exception {
        when(marketQueryService.resolveCurrentPrice("BTCUSDT")).thenReturn(currentPriceSnapshot());

        mockMvc.perform(get("/api/v1/markets/symbol/BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.data.instrumentType").value("CRYPTO"))
                .andExpect(jsonPath("$.data.priceStatus").value("LIVE"));
    }

    @Test
    void getBySymbolReturnsUnavailableInsteadOfServerError() throws Exception {
        when(marketQueryService.resolveCurrentPrice("TCD")).thenReturn(CurrentPriceSnapshot.unavailable("TCD"));

        mockMvc.perform(get("/api/v1/markets/symbol/TCD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("TCD"))
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.priceStatus").value("UNAVAILABLE"));
    }

    @Test
    void getByTypeAllowsPublicAccess() throws Exception {
        when(marketQueryService.getAllQuotes()).thenReturn(List.of(currencyQuote(), stockQuote()));

        mockMvc.perform(get("/api/v1/markets/type/currency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].symbol").value("USDTRY"))
                .andExpect(jsonPath("$.data[0].instrumentType").value("CURRENCY"));
    }

    @Test
    void getByTypeReturnsWrappedApiResponse() throws Exception {
        when(marketQueryService.getAllQuotes()).thenReturn(List.of(currencyQuote(), stockQuote(), cryptoQuote()));

        mockMvc.perform(get("/api/v1/markets/type/crypto")
                        .header("X-Request-Id", "market-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "market-request-123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Market data fetched"))
                .andExpect(jsonPath("$.data[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.data[0].source").value("BINANCE"));
    }

    @Test
    void legacyPlainMarketPathDoesNotAttemptInstrumentTypeParsing() throws Exception {
        mockMvc.perform(get("/api/v1/markets/TCD"))
                .andExpect(status().isNotFound());
    }

    private MarketQuote currencyQuote() {
        return new MarketQuote(
                "USDTRY",
                "USD / TRY",
                InstrumentType.CURRENCY,
                new BigDecimal("38.650000"),
                null,
                "TRY",
                DataSource.EVDS,
                Instant.parse("2026-05-03T12:00:00Z"),
                Instant.parse("2026-05-03T12:00:00Z")
        );
    }

    private MarketQuote stockQuote() {
        return new MarketQuote(
                "AAPL",
                "Apple Inc.",
                InstrumentType.STOCK,
                new BigDecimal("210.120000"),
                new BigDecimal("1.2300"),
                "USD",
                DataSource.BIST,
                Instant.parse("2026-05-03T12:00:00Z"),
                Instant.parse("2026-05-03T12:00:00Z")
        );
    }

    private MarketQuote cryptoQuote() {
        return new MarketQuote(
                "BTCUSDT",
                "Bitcoin / Tether",
                InstrumentType.CRYPTO,
                new BigDecimal("93500.100000"),
                new BigDecimal("4.2000"),
                "USDT",
                DataSource.BINANCE,
                Instant.parse("2026-05-03T12:00:00Z"),
                Instant.parse("2026-05-03T12:00:00Z")
        );
    }

    private CurrentPriceSnapshot currentPriceSnapshot() {
        return new CurrentPriceSnapshot(
                "BTCUSDT",
                "Bitcoin / Tether",
                InstrumentType.CRYPTO,
                new BigDecimal("93500.100000"),
                new BigDecimal("4.2000"),
                "USDT",
                DataSource.BINANCE,
                Instant.parse("2026-05-03T12:00:00Z"),
                Instant.parse("2026-05-03T12:00:00Z"),
                MarketPriceStatus.LIVE,
                Instant.parse("2026-05-03T12:00:00Z"),
                true
        );
    }

    private MarketQuote macroQuote() {
        return new MarketQuote(
                "TCMBFAIZ",
                "TCMB Politika Faizi (%)",
                InstrumentType.MACRO_INDICATOR,
                new BigDecimal("46.000000"),
                new BigDecimal("0.5000"),
                "TRY",
                DataSource.EVDS,
                Instant.parse("2026-05-03T12:00:00Z"),
                Instant.parse("2026-05-03T12:00:00Z")
        );
    }
}
