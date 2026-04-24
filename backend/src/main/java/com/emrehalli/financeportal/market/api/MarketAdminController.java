package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.market.api.dto.MarketHistoryBackfillResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillService;
import com.emrehalli.financeportal.market.service.MarketRefreshService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets/admin")
public class MarketAdminController {

    private final MarketRefreshService marketRefreshService;
    private final MarketHistoryBackfillService marketHistoryBackfillService;
    private final MarketApiMapper marketApiMapper;

    public MarketAdminController(MarketRefreshService marketRefreshService,
                                 MarketHistoryBackfillService marketHistoryBackfillService,
                                 MarketApiMapper marketApiMapper) {
        this.marketRefreshService = marketRefreshService;
        this.marketHistoryBackfillService = marketHistoryBackfillService;
        this.marketApiMapper = marketApiMapper;
    }

    @PostMapping("/refresh")
    public List<MarketQuoteResponse> refreshAll() {
        return marketRefreshService.refreshAll()
                .stream()
                .map(marketApiMapper::toResponse)
                .toList();
    }

    @PostMapping("/history/backfill")
    public List<MarketHistoryBackfillResponse> backfillHistory(@RequestParam DataSource source,
                                                               @RequestParam(required = false) Integer days) {
        int lookbackDays = marketHistoryBackfillService.resolveLookbackDays(source, days);
        return marketHistoryBackfillService.backfill(source, days).stream()
                .map(result -> new MarketHistoryBackfillResponse(
                        result.source().name(),
                        lookbackDays,
                        result.received(),
                        result.saved(),
                        result.skippedDuplicate()
                ))
                .toList();
    }
}
