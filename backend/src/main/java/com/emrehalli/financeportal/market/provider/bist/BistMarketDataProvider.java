package com.emrehalli.financeportal.market.provider.bist;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.bist.client.BistDelayedClient;
import com.emrehalli.financeportal.market.provider.bist.client.YahooClient;
import com.emrehalli.financeportal.market.provider.bist.config.BistProviderProperties;
import com.emrehalli.financeportal.market.provider.bist.dto.BistQuoteResponse;
import com.emrehalli.financeportal.market.provider.bist.mapper.BistMapper;
import com.emrehalli.financeportal.market.provider.bist.support.BistRoundRobinState;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class BistMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(BistMarketDataProvider.class);
    private static final String YAHOO_LOW_FREQUENCY = "YAHOO_LOW_FREQUENCY";
    private static final int DEFAULT_BATCH_SIZE = 5;
    private final YahooClient yahooClient;
    private final BistDelayedClient delayedClient;
    private final BistProviderProperties properties;
    private final BistMapper mapper;
    private final SymbolNormalizer symbolNormalizer;
    private final BistRoundRobinState roundRobinState;
    private final Clock clock;
    private final InstrumentRegistryService instrumentRegistryService;

    public BistMarketDataProvider(YahooClient yahooClient,
                                  BistDelayedClient delayedClient,
                                  BistProviderProperties properties,
                                  BistMapper mapper,
                                  SymbolNormalizer symbolNormalizer,
                                  BistRoundRobinState roundRobinState,
                                  InstrumentRegistryService instrumentRegistryService) {
        this.yahooClient = yahooClient;
        this.delayedClient = delayedClient;
        this.properties = properties;
        this.mapper = mapper;
        this.symbolNormalizer = symbolNormalizer;
        this.roundRobinState = roundRobinState;
        this.clock = Clock.systemUTC();
        this.instrumentRegistryService = instrumentRegistryService;
    }

    @Override
    public DataSource source() {
        return DataSource.BIST;
    }

    @Override
    public boolean supports(ProviderFetchRequest request) {
        if (!properties.isEnabled()) {
            return false;
        }

        if (request == null) {
            return true;
        }

        if (request.hasSourceFilter()) {
            return request.source() == DataSource.BIST;
        }

        return true;
    }

    @Override
    public ProviderFetchResult fetch(ProviderFetchRequest request) {
        List<String> symbols = resolveSymbols(request);
        if (symbols.isEmpty()) {
            log.info("BIST provider fetch skipped: no matching configured symbols");
            return new ProviderFetchResult(List.of(), List.of());
        }

        if (isHistoryRequest(request)) {
            List<MarketHistoryRecord> historyRecords = fetchHistoryRecords(symbols, request);
            log.info(
                    "BIST provider history fetch completed: requestedSymbolCount={}, historyRecordCount={}",
                    symbols.size(),
                    historyRecords.size()
            );
            return new ProviderFetchResult(List.of(), historyRecords);
        }

        int batchSize = resolveBatchSize(symbols.size());
        BatchSelection batchSelection = selectBatch(symbols, batchSize, request != null && request.hasSymbolFilter());
        List<String> batchSymbols = batchSelection.symbols();

        log.info(
                "BIST provider fetch started: batchSize={}, startIndex={}, symbols={}",
                batchSymbols.size(),
                batchSelection.startIndex(),
                batchSymbols
        );

        if (batchSymbols.isEmpty()) {
            return new ProviderFetchResult(List.of(), List.of());
        }

        if (isYahooLowFrequencyMode() && roundRobinState.isCoolingDown(clock)) {
            log.info("BIST Yahoo cooldown active: symbols={}, cooldownMinutes={}", batchSymbols, properties.getCooldownMinutesOnRateLimit());
            return mapFallbackResult(batchSymbols);
        }

        List<BistQuoteResponse> responses = fetchQuotesChunked(batchSymbols, batchSize);
        if (responses.isEmpty()) {
            roundRobinState.markFailed();
            return new ProviderFetchResult(List.of(), List.of());
        }

        List<MarketQuote> quotes = mapper.toMarketQuotes(responses);
        List<MarketHistoryRecord> historyRecords = mapper.toHistoryRecords(responses);
        if (!explicitSymbolRequest(request)) {
            roundRobinState.markSuccess(symbols, batchSize);
        }
        log.info(
                "BIST provider fetch completed: requestedSymbolCount={}, quoteCount={}, historyRecordCount={}",
                batchSymbols.size(),
                quotes.size(),
                historyRecords.size()
        );
        return new ProviderFetchResult(quotes, historyRecords);
    }

    @Override
    public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
        return fetch(request).quotes();
    }

    private List<MarketHistoryRecord> fetchHistoryRecords(List<String> symbols, ProviderFetchRequest request) {
        return symbols.stream()
                .flatMap(symbol -> mapper.toHistoryRecordsFromHistoryPoints(
                        yahooClient.fetchDailyHistory(symbol, request == null ? null : request.from(), request == null ? null : request.to())
                ).stream())
                .toList();
    }

    private List<BistQuoteResponse> fetchYahoo(List<String> batchSymbols) {
        if (!isYahooLowFrequencyMode() || !properties.getYahoo().isEnabled()) {
            return List.of();
        }

        log.info("BIST Yahoo fetch started: batchSize={}, symbols={}", batchSymbols.size(), batchSymbols);

        try {
            YahooClient.FetchResult result = yahooClient.fetchQuotes(batchSymbols);
            if (result.rateLimited()) {
                roundRobinState.markRateLimited(Duration.ofMinutes(Math.max(properties.getCooldownMinutesOnRateLimit(), 1L)), clock);
                log.warn(
                        "BIST Yahoo rate limited: status={}, cooldownMinutes={}",
                        result.statusCode(),
                        properties.getCooldownMinutesOnRateLimit()
                );
                return List.of();
            }

            if (result.unauthorized()) {
                roundRobinState.markFailed();
                log.warn("BIST Yahoo unauthorized: status={}, keeping existing cache", result.statusCode());
                return List.of();
            }

            if (properties.getRequestDelayMs() > 0L) {
                sleepQuietly(properties.getRequestDelayMs());
            }

            log.info(
                    "BIST Yahoo fetch completed: requestedSymbolCount={}, quoteCount={}",
                    batchSymbols.size(),
                    result.responses().size()
            );
            return result.responses();
        } catch (Exception ex) {
            log.warn("BIST Yahoo fetch failed: error={}", ex.getMessage(), ex);
            return List.of();
        }
    }

    private List<BistQuoteResponse> fetchQuotesChunked(List<String> symbols, int maxBatchSize) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        int effectiveBatchSize = Math.max(Math.min(maxBatchSize, symbols.size()), 1);
        List<BistQuoteResponse> mergedResponses = new ArrayList<>();

        for (int index = 0; index < symbols.size(); index += effectiveBatchSize) {
            List<String> chunkSymbols = List.copyOf(symbols.subList(index, Math.min(index + effectiveBatchSize, symbols.size())));
            List<BistQuoteResponse> chunkResponses = fetchYahoo(chunkSymbols);
            if (chunkResponses.isEmpty()) {
                chunkResponses = fetchFallback(chunkSymbols);
                if (chunkResponses.isEmpty()) {
                    log.warn(
                            "BIST provider chunk returned no data: chunkStart={}, chunkSize={}, symbols={}",
                            index,
                            chunkSymbols.size(),
                            chunkSymbols
                    );
                    continue;
                }
            }
            mergedResponses.addAll(chunkResponses);
        }

        return List.copyOf(mergedResponses);
    }

    private List<BistQuoteResponse> fetchFallback(List<String> batchSymbols) {
        log.info("BIST fallback used: fallbackSource={}", properties.getFallbackSource());
        return delayedClient.fetchQuotes(batchSymbols);
    }

    private ProviderFetchResult mapFallbackResult(List<String> batchSymbols) {
        List<BistQuoteResponse> fallbackResponses = fetchFallback(batchSymbols);
        if (fallbackResponses.isEmpty()) {
            return new ProviderFetchResult(List.of(), List.of());
        }

        return new ProviderFetchResult(
                mapper.toMarketQuotes(fallbackResponses),
                mapper.toHistoryRecords(fallbackResponses)
        );
    }

    private List<String> resolveSymbols(ProviderFetchRequest request) {
        List<ConfiguredSymbol> configuredSymbols = configuredSymbols();

        if (request == null || !request.hasSymbolFilter()) {
            return configuredSymbols.stream()
                    .map(ConfiguredSymbol::providerSymbol)
                    .toList();
        }

        Set<String> requestedSymbols = request.symbols().stream()
                .map(this::requestSymbolKey)
                .filter(symbol -> !symbol.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return configuredSymbols.stream()
                .filter(configuredSymbol -> requestedSymbols.contains(configuredSymbol.symbolKey()))
                .map(ConfiguredSymbol::providerSymbol)
                .toList();
    }

    private List<ConfiguredSymbol> configuredSymbols() {
        InstrumentRegistryService.Resolution resolution = instrumentRegistryService.resolveMappings(DataSource.BIST);
        List<ConfiguredSymbol> configuredSymbols = resolution.mappings().stream()
                .map(mapping -> new ConfiguredSymbol(
                        canonicalSymbol(mapping.providerSymbol()),
                        requestSymbolKey(mapping.symbol())
                ))
                .filter(symbol -> !symbol.providerSymbol().isBlank() && !symbol.symbolKey().isBlank())
                .distinct()
                .toList();

        log.info(
                "Market provider registry resolved: providerSource={}, registryMappingCount={}, resolvedSymbols={}",
                DataSource.BIST,
                resolution.mappings().size(),
                configuredSymbols.stream().map(ConfiguredSymbol::providerSymbol).toList()
        );
        return configuredSymbols;
    }

    private BatchSelection selectBatch(List<String> symbols, int requestedBatchSize, boolean explicitSymbolRequest) {
        if (symbols.isEmpty()) {
            return new BatchSelection(0, List.of());
        }

        if (explicitSymbolRequest) {
            return new BatchSelection(0, symbols);
        }

        BistRoundRobinState.BatchSelection stateBatch = roundRobinState.nextBatch(symbols, requestedBatchSize);
        if (stateBatch.symbols().size() == requestedBatchSize || stateBatch.symbols().isEmpty()) {
            return new BatchSelection(stateBatch.startIndex(), stateBatch.symbols());
        }

        List<String> batchSymbols = new ArrayList<>(stateBatch.symbols());
        int nextIndex = 0;
        while (batchSymbols.size() < requestedBatchSize) {
            batchSymbols.add(symbols.get(nextIndex));
            nextIndex++;
        }

        return new BatchSelection(stateBatch.startIndex(), List.copyOf(batchSymbols));
    }

    private int resolveBatchSize(int symbolCount) {
        int configuredBatchSize = properties.getBatchSize() > 0 ? properties.getBatchSize() : DEFAULT_BATCH_SIZE;
        return Math.min(configuredBatchSize, symbolCount);
    }

    private boolean explicitSymbolRequest(ProviderFetchRequest request) {
        return request != null && request.hasSymbolFilter();
    }

    private boolean isHistoryRequest(ProviderFetchRequest request) {
        return request != null && (request.from() != null || request.to() != null);
    }

    private String canonicalSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String requestSymbolKey(String symbol) {
        String canonical = canonicalSymbol(symbol);
        if (canonical.endsWith(".IS")) {
            canonical = canonical.substring(0, canonical.length() - 3);
        }
        return symbolNormalizer.normalize(canonical).orElse(canonical);
    }

    private boolean isYahooLowFrequencyMode() {
        String providerMode = properties.getProviderMode();
        return providerMode != null && YAHOO_LOW_FREQUENCY.equalsIgnoreCase(providerMode.trim());
    }

    private void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record BatchSelection(int startIndex, List<String> symbols) {
    }

    private record ConfiguredSymbol(String providerSymbol, String symbolKey) {
    }
}
