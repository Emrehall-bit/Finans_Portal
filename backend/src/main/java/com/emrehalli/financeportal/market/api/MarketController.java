package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketController {

    private final MarketQueryService marketQueryService;
    private final MarketApiMapper marketApiMapper;
    private final AppMessageSource appMessageSource;

    public MarketController(MarketQueryService marketQueryService,
                            MarketApiMapper marketApiMapper,
                            AppMessageSource appMessageSource) {
        this.marketQueryService = marketQueryService;
        this.marketApiMapper = marketApiMapper;
        this.appMessageSource = appMessageSource;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MarketQuoteResponse>>> getMarkets() {
        List<MarketQuoteResponse> quotes = marketQueryService.getAllQuotes().stream()
                .map(marketApiMapper::toResponse)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.<List<MarketQuoteResponse>>builder()
                        .success(true)
                        .message(appMessageSource.get("market.dataFetched"))
                        .data(quotes)
                        .build()
        );
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<MarketQuoteResponse>>> getByType(@PathVariable String type) {
        InstrumentType instrumentType = InstrumentType.valueOf(type.toUpperCase(Locale.ROOT));
        List<MarketQuoteResponse> prices = marketQueryService.getAllQuotes().stream()
                .filter(quote -> quote != null && compatibleTypes(instrumentType).contains(quote.instrumentType()))
                .map(marketApiMapper::toResponse)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.<List<MarketQuoteResponse>>builder()
                        .success(true)
                        .message(appMessageSource.get("market.dataFetched"))
                        .data(prices)
                        .build()
        );
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<ApiResponse<MarketQuoteResponse>> getBySymbol(@PathVariable String symbol) {
        MarketQuoteResponse quote = marketApiMapper.toResponse(marketQueryService.resolveCurrentPrice(symbol));

        return ResponseEntity.ok(
                ApiResponse.<MarketQuoteResponse>builder()
                        .success(true)
                        .message(appMessageSource.get("market.dataFetched"))
                        .data(quote)
                        .build()
        );
    }

    private Set<InstrumentType> compatibleTypes(InstrumentType requestedType) {
        return switch (requestedType) {
            case CURRENCY, FX -> EnumSet.of(InstrumentType.CURRENCY, InstrumentType.FX);
            case COMMODITY, GOLD -> EnumSet.of(InstrumentType.COMMODITY, InstrumentType.GOLD);
            default -> EnumSet.of(requestedType);
        };
    }
}
