package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class MarketOverviewService {

    private final MarketCacheService marketCacheService;
    private final ProviderOrchestrationService providerOrchestrationService;
    private final MarketRefreshService marketRefreshService;

    public MarketOverviewService(MarketCacheService marketCacheService,
                                 ProviderOrchestrationService providerOrchestrationService,
                                 MarketRefreshService marketRefreshService) {
        this.marketCacheService = marketCacheService;
        this.providerOrchestrationService = providerOrchestrationService;
        this.marketRefreshService = marketRefreshService;
    }

    public List<MarketQuote> getByType(InstrumentType type) {
        List<MarketQuote> cachedQuotes = filterByType(loadAvailableQuotes(), type);
        if (!cachedQuotes.isEmpty()) {
            return cachedQuotes;
        }

        refreshSources(type);
        return filterByType(loadAvailableQuotes(), type);
    }

    private List<MarketQuote> loadAvailableQuotes() {
        List<MarketQuote> aggregateQuotes = marketCacheService.getAllQuotes();
        if (!aggregateQuotes.isEmpty()) {
            return aggregateQuotes;
        }

        return marketCacheService.rebuildAllQuotes(providerOrchestrationService.availableSources());
    }

    private void refreshSources(InstrumentType type) {
        for (DataSource source : sourcesFor(type)) {
            marketRefreshService.refreshSource(source);
        }
    }

    private List<DataSource> sourcesFor(InstrumentType type) {
        if (type == null) {
            return List.of();
        }

        return switch (type) {
            case CURRENCY, FX -> List.of(DataSource.EVDS);
            case STOCK -> List.of(DataSource.BIST);
            case CRYPTO -> List.of(DataSource.BINANCE);
            default -> List.of();
        };
    }

    private List<MarketQuote> filterByType(List<MarketQuote> quotes, InstrumentType type) {
        if (quotes == null || quotes.isEmpty() || type == null) {
            return List.of();
        }

        Set<InstrumentType> compatibleTypes = compatibleTypes(type);
        return quotes.stream()
                .filter(quote -> quote != null && compatibleTypes.contains(quote.instrumentType()))
                .toList();
    }

    private Set<InstrumentType> compatibleTypes(InstrumentType requestedType) {
        return switch (requestedType) {
            case CURRENCY, FX -> EnumSet.of(InstrumentType.CURRENCY, InstrumentType.FX);
            case COMMODITY, GOLD -> EnumSet.of(InstrumentType.COMMODITY, InstrumentType.GOLD);
            default -> EnumSet.of(requestedType);
        };
    }

}
