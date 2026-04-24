package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketController {

    private final MarketQueryService marketQueryService;
    private final MarketApiMapper marketApiMapper;

    public MarketController(MarketQueryService marketQueryService,
                            MarketApiMapper marketApiMapper) {
        this.marketQueryService = marketQueryService;
        this.marketApiMapper = marketApiMapper;
    }

    @GetMapping
    public List<MarketQuoteResponse> getAllQuotes() {
        return marketQueryService.getAllQuotes()
                .stream()
                .map(marketApiMapper::toResponse)
                .toList();
    }

    @GetMapping("/{symbol}")
    public MarketQuoteResponse getBySymbol(@PathVariable String symbol) {
        return marketApiMapper.toResponse(
                marketQueryService.getQuoteBySymbol(symbol.toUpperCase())
        );
    }
}