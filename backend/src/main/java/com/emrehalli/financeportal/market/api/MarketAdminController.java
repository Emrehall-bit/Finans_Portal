package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketBackfillJobResponse;
import com.emrehalli.financeportal.market.api.dto.MarketHistoryBackfillResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.api.dto.admin.MarketProvidersHealthResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillService;
import com.emrehalli.financeportal.market.service.MarketProviderHealthService;
import com.emrehalli.financeportal.market.service.MarketRefreshService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/markets")
public class MarketAdminController {

    private final MarketRefreshService marketRefreshService;
    private final MarketHistoryBackfillService marketHistoryBackfillService;
    private final MarketProviderHealthService marketProviderHealthService;
    private final MarketApiMapper marketApiMapper;
    private final MarketApiResponseFactory responseFactory;

    public MarketAdminController(MarketRefreshService marketRefreshService,
                                 MarketHistoryBackfillService marketHistoryBackfillService,
                                 MarketProviderHealthService marketProviderHealthService,
                                 MarketApiMapper marketApiMapper,
                                 MarketApiResponseFactory responseFactory) {
        this.marketRefreshService = marketRefreshService;
        this.marketHistoryBackfillService = marketHistoryBackfillService;
        this.marketProviderHealthService = marketProviderHealthService;
        this.marketApiMapper = marketApiMapper;
        this.responseFactory = responseFactory;
    }

    /**
     * Triggers a full market refresh.
     *
     * Role: ADMIN.
     * Return type: wrapped refreshed quote list.
     */
    @PostMapping("/refresh")
    public ApiResponse<List<MarketQuoteResponse>> refreshAll() {
        List<MarketQuoteResponse> responses = marketRefreshService.refreshAll()
                .stream()
                .map(marketApiMapper::toResponse)
                .toList();
        return responseFactory.success(responses, "market.dataFetched");
    }

    /**
     * Triggers historical backfill for a provider.
     *
     * Role: ADMIN.
     * Return type: wrapped backfill persistence summary.
     */
    @PostMapping("/history/backfill")
    public ApiResponse<List<MarketHistoryBackfillResponse>> backfillHistory(@RequestParam DataSource source,
                                                                            @RequestParam(required = false) Integer days) {
        int lookbackDays = marketHistoryBackfillService.resolveLookbackDays(source, days);
        List<MarketHistoryBackfillResponse> responses = marketHistoryBackfillService.backfill(source, days).stream()
                .map(result -> marketApiMapper.toBackfillResponse(result, lookbackDays))
                .toList();
        return responseFactory.success(responses, "market.dataFetched");
    }

    /**
     * Triggers a manual backfill job for a provider symbol.
     *
     * Role: ADMIN.
     * Return type: wrapped backfill job result.
     */
    @PostMapping("/backfill/{source}/{symbol}")
    public ApiResponse<MarketBackfillJobResponse> triggerBackfill(@PathVariable DataSource source, @PathVariable String symbol) {
        MarketBackfillJobResponse response = marketApiMapper.toBackfillJobResponse(
                marketHistoryBackfillService.triggerManual(source, symbol)
        );
        return responseFactory.success(response, "market.dataFetched");
    }

    /**
     * Returns provider health and circuit breaker state.
     *
     * Role: ADMIN.
     * Return type: wrapped provider health list.
     */
    @GetMapping("/health")
    public ApiResponse<MarketProvidersHealthResponse> getProviderHealth() {
        MarketProvidersHealthResponse response = new MarketProvidersHealthResponse(
                marketProviderHealthService.getProviderHealth().stream()
                        .map(marketApiMapper::toProviderHealthResponse)
                        .toList()
        );
        return responseFactory.success(response, "market.dataFetched");
    }
}
