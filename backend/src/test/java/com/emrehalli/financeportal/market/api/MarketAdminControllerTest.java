package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillService;
import com.emrehalli.financeportal.market.service.MarketProviderHealthService;
import com.emrehalli.financeportal.market.service.MarketRefreshService;
import com.emrehalli.financeportal.market.service.model.BackfillRunStatus;
import com.emrehalli.financeportal.market.service.model.MarketBackfillJobResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketAdminController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class, MarketApiMapper.class, MarketApiResponseFactory.class})
class MarketAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketRefreshService marketRefreshService;

    @MockBean
    private MarketHistoryBackfillService marketHistoryBackfillService;

    @MockBean
    private MarketProviderHealthService marketProviderHealthService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @MockBean
    private AppMessageSource appMessageSource;

    @BeforeEach
    void setUp() {
        when(appMessageSource.get("market.dataFetched")).thenReturn("Market data fetched");
    }

    @Test
    void backfillHistoryAcceptsBinanceSource() throws Exception {
        when(marketHistoryBackfillService.resolveLookbackDays(DataSource.BINANCE, 365))
                .thenReturn(365);
        when(marketHistoryBackfillService.backfill(DataSource.BINANCE, 365))
                .thenReturn(List.of(new MarketHistoryPersistenceResult(DataSource.BINANCE, 365, 365, 0)));

        mockMvc.perform(post("/api/v1/admin/markets/history/backfill")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN"))
                        .param("source", "BINANCE")
                        .param("days", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Market data fetched"))
                .andExpect(jsonPath("$.data[0].source").value("BINANCE"))
                .andExpect(jsonPath("$.data[0].lookbackDays").value(365))
                .andExpect(jsonPath("$.data[0].saved").value(365));
    }

    @Test
    void backfillHistoryRejectsNonAdminUsers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/markets/history/backfill")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .param("source", "BINANCE")
                        .param("days", "365"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanTriggerManualBackfill() throws Exception {
        when(marketHistoryBackfillService.triggerManual(DataSource.BINANCE, "BTCUSDT"))
                .thenReturn(new MarketBackfillJobResult(
                        DataSource.BINANCE,
                        "BTCUSDT",
                        BackfillRunStatus.SUCCESS,
                        2,
                        2,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 4, 30),
                        0,
                        null
                ));

        mockMvc.perform(post("/api/v1/admin/markets/backfill/BINANCE/BTCUSDT")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Market data fetched"))
                .andExpect(jsonPath("$.data.providerSource").value("BINANCE"))
                .andExpect(jsonPath("$.data.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void nonAdminCannotTriggerManualBackfill() throws Exception {
        mockMvc.perform(post("/api/v1/admin/markets/backfill/BINANCE/BTCUSDT")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadMarketProviderHealth() throws Exception {
        when(marketProviderHealthService.getProviderHealth()).thenReturn(List.of(
                new MarketProviderHealthService.ProviderHealthSnapshot(
                        DataSource.BINANCE,
                        "CLOSED",
                        Instant.parse("2026-05-06T10:00:00Z"),
                        Instant.parse("2026-05-06T09:00:00Z"),
                        0,
                        12
                )
        ));

        mockMvc.perform(get("/api/v1/admin/markets/health")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Market data fetched"))
                .andExpect(jsonPath("$.data.providers[0].source").value("BINANCE"))
                .andExpect(jsonPath("$.data.providers[0].circuitBreakerState").value("CLOSED"))
                .andExpect(jsonPath("$.data.providers[0].failedMappingCount").value(0))
                .andExpect(jsonPath("$.data.providers[0].totalMappingCount").value(12));
    }

    @Test
    void nonAdminCannotReadMarketProviderHealth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/markets/health")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }
}
