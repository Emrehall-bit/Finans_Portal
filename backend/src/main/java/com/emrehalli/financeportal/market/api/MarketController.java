package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.market.support.InstrumentTypeAliasResolver;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketController {

    private final MarketQueryService marketQueryService;
    private final MarketApiMapper marketApiMapper;
    private final MarketApiResponseFactory responseFactory;
    private final InstrumentTypeAliasResolver instrumentTypeAliasResolver;

    public MarketController(MarketQueryService marketQueryService,
                            MarketApiMapper marketApiMapper,
                            MarketApiResponseFactory responseFactory,
                            InstrumentTypeAliasResolver instrumentTypeAliasResolver) {
        this.marketQueryService = marketQueryService;
        this.marketApiMapper = marketApiMapper;
        this.responseFactory = responseFactory;
        this.instrumentTypeAliasResolver = instrumentTypeAliasResolver;
    }

    /**
     * Returns all cached market quotes.
     *
     * Role: public endpoint.
     * Return type: wrapped quote list.
     */
    @GetMapping
    public ApiResponse<List<MarketQuoteResponse>> getMarkets(@RequestParam(value = "type", required = false) String type) {
        if (type == null || type.isBlank()) {
            List<MarketQuoteResponse> quotes = marketQueryService.getDefaultQuotes().stream()
                    .map(marketApiMapper::toResponse)
                    .toList();
            return responseFactory.success(quotes, "market.dataFetched");
        }

        return responseFactory.success(resolveQuotesByType(type), "market.dataFetched");
    }

    /**
     * Returns market quotes filtered by instrument type aliases.
     *
     * Role: public endpoint.
     * Return type: wrapped quote list.
     */
    @GetMapping("/type/{type}")
    public ApiResponse<List<MarketQuoteResponse>> getByType(@PathVariable String type) {
        return responseFactory.success(resolveQuotesByType(type), "market.dataFetched");
    }

    /**
     * Returns the current price snapshot for a symbol.
     *
     * Role: public endpoint.
     * Return type: wrapped current price response.
     */
    @GetMapping("/symbol/{symbol}")
    public ApiResponse<MarketQuoteResponse> getBySymbol(@PathVariable String symbol) {
        MarketQuoteResponse quote = marketApiMapper.toResponse(marketQueryService.resolveCurrentPrice(symbol));
        return responseFactory.success(quote, "market.dataFetched");
    }

    private List<MarketQuoteResponse> resolveQuotesByType(String type) {
        InstrumentType instrumentType = instrumentTypeAliasResolver.resolve(type);
        return marketQueryService.getAllQuotes().stream()
                .filter(quote -> quote != null && instrumentTypeAliasResolver.compatibleTypes(instrumentType).contains(quote.instrumentType()))
                .map(marketApiMapper::toResponse)
                .toList();
    }
}
