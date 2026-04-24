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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class BistMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(BistMarketDataProvider.class);
    private static final String YAHOO_LOW_FREQUENCY = "YAHOO_LOW_FREQUENCY";

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
        return properties.isEnabled()
                && (request == null || !request.hasSourceFilter() || request.source() == DataSource.BIST)
                && (request == null || !request.hasInstrumentTypeFilter() || request.instrumentTypes().contains(InstrumentType.STOCK));
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
                : Math.max(properties.getBatchSize(), 1);
        BistRoundRobinState.BatchSelection batchSelection = roundRobinState.nextBatch(symbols, batchSize);
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

        roundRobinState.markSuccess(symbols, batchSize);

        List<MarketQuote> quotes = mapper.toMarketQuotes(responses);
        List<MarketHistoryRecord> historyRecords = mapper.toHistoryRecords(responses);
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

    private boolean isYahooLowFrequencyMode() {
        return properties.getProviderMode() != null
                && YAHOO_LOW_FREQUENCY.equalsIgnoreCase(properties.getProviderMode().trim().toUpperCase(Locale.ROOT));
    }

    private void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
