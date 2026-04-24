package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillService;
import com.emrehalli.financeportal.market.service.MarketRefreshService;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketAdminController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class, MarketApiMapper.class})
class MarketAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketRefreshService marketRefreshService;

    @MockBean
    private MarketHistoryBackfillService marketHistoryBackfillService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @Test
    void backfillHistoryAcceptsBinanceSource() throws Exception {
        when(marketHistoryBackfillService.resolveLookbackDays(DataSource.BINANCE, 365))
                .thenReturn(365);
        when(marketHistoryBackfillService.backfill(DataSource.BINANCE, 365))
                .thenReturn(List.of(new MarketHistoryPersistenceResult(DataSource.BINANCE, 365, 365, 0)));

        mockMvc.perform(post("/api/v1/markets/admin/history/backfill")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN"))
                        .param("source", "BINANCE")
                        .param("days", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("BINANCE"))
                .andExpect(jsonPath("$[0].lookbackDays").value(365))
                .andExpect(jsonPath("$[0].saved").value(365));
    }
}
