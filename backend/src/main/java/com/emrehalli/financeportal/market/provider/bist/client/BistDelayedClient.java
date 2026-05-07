package com.emrehalli.financeportal.market.provider.bist.client;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.provider.bist.config.BistProviderProperties;
import com.emrehalli.financeportal.market.provider.bist.dto.BistQuoteResponse;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class BistDelayedClient {

    private static final Logger log = LoggerFactory.getLogger(BistDelayedClient.class);

    private final BistProviderProperties properties;
    private final MarketCacheService marketCacheService;
    private final SymbolNormalizer symbolNormalizer;

    public BistDelayedClient(BistProviderProperties properties,
                             MarketCacheService marketCacheService,
                             SymbolNormalizer symbolNormalizer) {
        this.properties = properties;
        this.marketCacheService = marketCacheService;
        this.symbolNormalizer = symbolNormalizer;
    }

    /**
     * Returns cached BIST quotes as stale fallback values when Yahoo is unavailable.
     *
     * @param symbols provider symbols
     * @return stale cached quotes when available
     */
    public List<BistQuoteResponse> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.warn("BIST delayed fallback skipped: enabled={}, requestedSymbolCount=0", properties.getDelayed().isEnabled());
            return List.of();
        }

        List<BistQuoteResponse> cachedResponses = symbols.stream()
                .map(this::toCachedResponse)
                .flatMap(Optional::stream)
                .toList();

        if (cachedResponses.isEmpty()) {
            log.warn(
                    "BIST delayed fallback cache miss: enabled={}, requestedSymbolCount={}",
                    properties.getDelayed().isEnabled(),
                    symbols.size()
            );
            return List.of();
        }

        log.warn(
                "BIST delayed fallback served stale cache: enabled={}, requestedSymbolCount={}, returnedCount={}",
                properties.getDelayed().isEnabled(),
                symbols.size(),
                cachedResponses.size()
        );
        return cachedResponses;
    }

    private Optional<BistQuoteResponse> toCachedResponse(String providerSymbol) {
        String canonicalSymbol = canonicalSymbol(providerSymbol);
        return marketCacheService.getQuoteBySymbol(canonicalSymbol)
                .map(quote -> new BistQuoteResponse(
                        providerSymbol == null || providerSymbol.isBlank() ? quote.symbol() + ".IS" : providerSymbol,
                        quote.displayName(),
                        quote.displayName(),
                        quote.price(),
                        quote.changeRate(),
                        resolveEpochSeconds(quote)
                ));
    }

    private Long resolveEpochSeconds(MarketQuote quote) {
        Instant priceInstant = quote.priceTime() != null ? quote.priceTime() : quote.fetchedAt();
        return priceInstant == null ? null : priceInstant.getEpochSecond();
    }

    private String canonicalSymbol(String providerSymbol) {
        String rawSymbol = providerSymbol == null ? "" : providerSymbol.trim().toUpperCase(Locale.ROOT);
        if (rawSymbol.endsWith(".IS")) {
            rawSymbol = rawSymbol.substring(0, rawSymbol.length() - 3);
        }
        return symbolNormalizer.normalize(rawSymbol).orElse(rawSymbol);
    }
}
