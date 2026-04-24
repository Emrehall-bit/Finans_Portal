package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.market.api.dto.MarketHistoryResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.MarketHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketHistoryController {

    private final MarketHistoryService marketHistoryService;
    private final MarketApiMapper marketApiMapper;

    public MarketHistoryController(MarketHistoryService marketHistoryService,
                                   MarketApiMapper marketApiMapper) {
        this.marketHistoryService = marketHistoryService;
        this.marketApiMapper = marketApiMapper;
    }

    @GetMapping("/{symbol}/history")
    public List<MarketHistoryResponse> getHistory(
            @PathVariable String symbol,
            @RequestParam(required = false) DataSource source,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return marketHistoryService.getHistory(symbol, source, startDate, endDate).stream()
                .map(marketApiMapper::toHistoryResponse)
                .toList();
    }
}
