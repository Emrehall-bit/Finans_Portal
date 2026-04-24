package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.exception.MarketDataNotFoundException;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketQueryService {

    private final MarketCacheService marketCacheService;
    private final SymbolNormalizer symbolNormalizer;

    public MarketQueryService(MarketCacheService marketCacheService,
                              SymbolNormalizer symbolNormalizer) {
        this.marketCacheService = marketCacheService;
        this.symbolNormalizer = symbolNormalizer;
    }

    public List<MarketQuote> getAllQuotes() {
        return marketCacheService.getAllQuotes();
    }

    public MarketQuote getQuoteBySymbol(String symbol) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol)
                .orElseThrow(() -> new MarketDataNotFoundException("Market quote not found for symbol: " + symbol));

        return marketCacheService.getQuoteBySymbol(canonicalSymbol)
                .orElseThrow(() -> new MarketDataNotFoundException("Market quote not found for symbol: " + canonicalSymbol));
    }
}
