package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/funds")
public class FundController {

    private final MarketQueryService marketQueryService;
    private final MarketApiMapper marketApiMapper;
    private final MarketApiResponseFactory responseFactory;

    public FundController(MarketQueryService marketQueryService,
                          MarketApiMapper marketApiMapper,
                          MarketApiResponseFactory responseFactory) {
        this.marketQueryService = marketQueryService;
        this.marketApiMapper = marketApiMapper;
        this.responseFactory = responseFactory;
    }

    @GetMapping
    public ApiResponse<List<MarketQuoteResponse>> getFunds() {
        List<MarketQuoteResponse> funds = marketQueryService.getAllQuotes().stream()
                .filter(quote -> quote != null && quote.instrumentType() == InstrumentType.FUND)
                .map(marketApiMapper::toResponse)
                .toList();
        return responseFactory.success(funds, "market.dataFetched");
    }

    @GetMapping("/{symbol}")
    public ApiResponse<MarketQuoteResponse> getFundBySymbol(@PathVariable String symbol) {
        MarketQuoteResponse quote = marketApiMapper.toResponse(marketQueryService.resolveCurrentPrice(symbol));
        return responseFactory.success(quote, "market.dataFetched");
    }
}
