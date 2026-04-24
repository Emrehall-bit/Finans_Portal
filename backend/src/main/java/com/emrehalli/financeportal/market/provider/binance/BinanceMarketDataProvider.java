package com.emrehalli.financeportal.market.provider.binance;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.binance.client.BinanceClient;
import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.binance.mapper.BinanceMapper;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketDataProvider.class);

    private final BinanceClient binanceClient;
    private final BinanceProviderProperties properties;
    private final BinanceMapper mapper;
    private final SymbolNormalizer symbolNormalizer;

    public BinanceMarketDataProvider(BinanceClient binanceClient,
                                     BinanceProviderProperties properties,
                                     BinanceMapper mapper,
                                     SymbolNormalizer symbolNormalizer) {
        this.binanceClient = binanceClient;
        this.properties = properties;
        this.mapper = mapper;
        this.symbolNormalizer = symbolNormalizer;
    }

    @Override
    public DataSource source() {
        return DataSource.BINANCE;
    }

    @Override
    public boolean supports(ProviderFetchRequest request) {
        return properties.isEnabled()
                && (request == null || !request.hasSourceFilter() || request.source() == DataSource.BINANCE)
                && (request == null || !request.hasInstrumentTypeFilter() || request.instrumentTypes().contains(InstrumentType.CRYPTO));
    }

    @Override
    public ProviderFetchResult fetch(ProviderFetchRequest request) {
        List<String> symbols = resolveSymbols(request);
        if (symbols.isEmpty()) {
            log.info("Binance provider fetch skipped: no matching configured symbols");
            return new ProviderFetchResult(List.of(), List.of());
        }

        try {
            List<MarketQuote> quotes = shouldFetchQuotes(request)
                    ? mapper.toMarketQuotes(binanceClient.fetchTickers(symbols))
                    : List.of();
            List<MarketHistoryRecord> historyRecords = shouldFetchHistory(request)
                    ? fetchHistoryRecords(symbols, request)
                    : List.of();
            log.info(
                    "Binance provider fetch completed: requestedSymbolCount={}, quoteCount={}, historyRecordCount={}",
                    symbols.size(),
                    quotes.size(),
                    historyRecords.size()
            );
            return new ProviderFetchResult(quotes, historyRecords);
        } catch (Exception ex) {
            log.warn("Binance provider fetch failed: error={}", ex.getMessage(), ex);
            return new ProviderFetchResult(List.of(), List.of());
        }
    }

    @Override
    public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
        return fetch(request).quotes();
    }

    private List<MarketHistoryRecord> fetchHistoryRecords(List<String> symbols, ProviderFetchRequest request) {
        return symbols.stream()
                .flatMap(symbol -> fetchHistoryForSymbol(symbol, request).stream())
                .toList();
    }

    private List<MarketHistoryRecord> fetchHistoryForSymbol(String symbol, ProviderFetchRequest request) {
        try {
            return mapper.toHistoryRecords(
                    symbol,
                    binanceClient.fetchDailyKlines(symbol, request == null ? null : request.from(), request == null ? null : request.to())
            );
        } catch (Exception ex) {
            log.warn("Binance provider history fetch failed: symbol={}, error={}", symbol, ex.getMessage(), ex);
            return List.of();
        }
    }

    private boolean shouldFetchQuotes(ProviderFetchRequest request) {
        return request == null || (request.from() == null && request.to() == null);
    }

    private boolean shouldFetchHistory(ProviderFetchRequest request) {
        return request != null && (request.from() != null || request.to() != null);
    }

    private List<String> resolveSymbols(ProviderFetchRequest request) {
        List<String> configuredSymbols = properties.getSymbols().stream()
                .flatMap(symbol -> symbolNormalizer.normalize(symbol).stream())
                .distinct()
                .toList();

        if (request == null || !request.hasSymbolFilter()) {
            return configuredSymbols;
        }

        Set<String> requestedSymbols = request.symbols().stream()
                .flatMap(symbol -> symbolNormalizer.normalize(symbol).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return configuredSymbols.stream()
                .filter(requestedSymbols::contains)
                .toList();
    }
}
