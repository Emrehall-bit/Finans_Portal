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
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class BistMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(BistMarketDataProvider.class);
    private static final String YAHOO_LOW_FREQUENCY = "YAHOO_LOW_FREQUENCY";
    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final List<String> DEFAULT_BIST_SYMBOLS = List.of(
            "THYAO.IS",
            "ASELS.IS",
            "GARAN.IS",
            "AKBNK.IS",
            "BIMAS.IS",
            "KCHOL.IS",
            "SAHOL.IS",
            "EREGL.IS",
            "TUPRS.IS",
            "FROTO.IS",
            "YKBNK.IS",
            "ISCTR.IS",
            "SISE.IS",
            "PGSUS.IS",
            "PETKM.IS",
            "KOZAL.IS",
            "KOZAA.IS",
            "ENKAI.IS",
            "ASTOR.IS",
            "HEKTS.IS",
            "GUBRF.IS",
            "ALARK.IS",
            "TOASO.IS",
            "BRSAN.IS",
            "CCOLA.IS",
            "CIMSA.IS",
            "DOHOL.IS",
            "ODAS.IS",
            "OYAKC.IS",
            "VESTL.IS"
    );

    private final YahooClient yahooClient;
    private final BistDelayedClient delayedClient;
    private final BistProviderProperties properties;
    private final BistMapper mapper;
    private final SymbolNormalizer symbolNormalizer;
    private final BistRoundRobinState roundRobinState;
    private final Clock clock;

    public BistMarketDataProvider(YahooClient yahooClient,
                                  BistDelayedClient delayedClient,
                                  BistProviderProperties properties,
                                  BistMapper mapper,
                                  SymbolNormalizer symbolNormalizer,
                                  BistRoundRobinState roundRobinState) {
        this.yahooClient = yahooClient;
        this.delayedClient = delayedClient;
        this.properties = properties;
        this.mapper = mapper;
        this.symbolNormalizer = symbolNormalizer;
        this.roundRobinState = roundRobinState;
        this.clock = Clock.systemUTC();
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

        int batchSize = request != null && request.hasSymbolFilter()
                ? symbols.size()
                : resolveBatchSize(symbols.size());
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
            return new ProviderFetchResult(List.of(), List.of());
        }

        List<BistQuoteResponse> responses = fetchYahoo(batchSymbols);
        if (responses.isEmpty()) {
            responses = fetchFallback(batchSymbols);
            if (responses.isEmpty()) {
                roundRobinState.markFailed();
                return new ProviderFetchResult(List.of(), List.of());
            }
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

    private List<BistQuoteResponse> fetchFallback(List<String> batchSymbols) {
        if (!properties.getDelayed().isEnabled()) {
            log.info("BIST fallback skipped: fallbackSource={}, enabled=false", properties.getFallbackSource());
            return List.of();
        }

        log.info("BIST fallback used: fallbackSource={}", properties.getFallbackSource());
        return delayedClient.fetchQuotes(batchSymbols);
    }

    private List<String> resolveSymbols(ProviderFetchRequest request) {
        List<String> configuredSymbols = configuredSymbols();

        if (request == null || !request.hasSymbolFilter()) {
            return configuredSymbols;
        }

        return configuredSymbols.stream()
                .filter(configuredSymbol -> request.symbols().stream().anyMatch(requested -> matchesSymbol(configuredSymbol, requested)))
                .toList();
    }

    private List<String> configuredSymbols() {
        List<String> rawSymbols = properties.getSymbols() == null || properties.getSymbols().isEmpty()
                ? DEFAULT_BIST_SYMBOLS
                : properties.getSymbols();

        return rawSymbols.stream()
                .map(this::canonicalSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
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

    private boolean matchesSymbol(String configuredSymbol, String requestedSymbol) {
        return canonicalSymbol(configuredSymbol).equals(canonicalSymbol(requestedSymbol));
    }

    private String canonicalSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
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
}
