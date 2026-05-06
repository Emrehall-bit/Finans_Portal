package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.market.api.dto.MarketBackfillJobResponse;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.MarketHistoryMaintenanceService;
import com.emrehalli.financeportal.market.service.model.MarketBackfillJobResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/market")
public class MarketBackfillAdminController {

    private final MarketHistoryMaintenanceService marketHistoryMaintenanceService;

    public MarketBackfillAdminController(MarketHistoryMaintenanceService marketHistoryMaintenanceService) {
        this.marketHistoryMaintenanceService = marketHistoryMaintenanceService;
    }

    @PostMapping("/backfill/{source}/{symbol}")
    public MarketBackfillJobResponse triggerBackfill(@PathVariable DataSource source, @PathVariable String symbol) {
        MarketBackfillJobResult result = marketHistoryMaintenanceService.triggerManual(source, symbol);
        return new MarketBackfillJobResponse(
                result.providerSource().name(),
                result.symbol(),
                result.status().name(),
                result.fetchedCount(),
                result.savedCount(),
                result.minDate(),
                result.maxDate(),
                result.retryCount(),
                result.message()
        );
    }
}
