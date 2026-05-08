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
import com.emrehalli.financeportal.market.service.model.CurrentPriceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundController.class)
@Import({
        SecurityConfig.class,
        KeycloakJwtRoleConverter.class,
        ObservabilityFilterConfig.class,
        GlobalExceptionHandler.class,
        MarketExceptionHandler.class,
        MarketApiMapper.class,
        MarketApiResponseFactory.class
})
class FundControllerTest {

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
    void getFundsReturnsOnlyFundQuotes() throws Exception {
        when(marketQueryService.getAllQuotes()).thenReturn(List.of(fundQuote(), stockQuote()));

        mockMvc.perform(get("/api/v1/funds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].symbol").value("AAL"))
                .andExpect(jsonPath("$.data[0].instrumentType").value("FUND"));
    }

    @Test
    void getFundBySymbolReturnsSnapshot() throws Exception {
        when(marketQueryService.resolveCurrentPrice("AAL")).thenReturn(
                new CurrentPriceSnapshot(
                        "AAL",
                        "Fund",
                        InstrumentType.FUND,
                        new BigDecimal("3.21"),
                        null,
                        "TRY",
                        DataSource.DB_FALLBACK,
                        Instant.parse("2026-05-08T00:00:00Z"),
                        Instant.parse("2026-05-08T00:00:00Z"),
                        MarketPriceStatus.STALE,
                        Instant.parse("2026-05-08T00:00:00Z"),
                        true
                )
        );

        mockMvc.perform(get("/api/v1/funds/AAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("AAL"))
                .andExpect(jsonPath("$.data.instrumentType").value("FUND"));
    }

    private MarketQuote fundQuote() {
        return new MarketQuote(
                "AAL",
                "Fund",
                InstrumentType.FUND,
                new BigDecimal("3.21"),
                null,
                "TRY",
                DataSource.DB_FALLBACK,
                Instant.parse("2026-05-08T00:00:00Z"),
                Instant.parse("2026-05-08T00:00:00Z"),
                MarketPriceStatus.STALE
        );
    }

    private MarketQuote stockQuote() {
        return new MarketQuote(
                "AAPL",
                "Apple",
                InstrumentType.STOCK,
                new BigDecimal("210.12"),
                null,
                "USD",
                DataSource.BIST,
                Instant.parse("2026-05-08T00:00:00Z"),
                Instant.parse("2026-05-08T00:00:00Z"),
                MarketPriceStatus.LIVE
        );
    }
}
