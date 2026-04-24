package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class EvdsMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(EvdsMarketDataProvider.class);

    private final EvdsClient evdsClient;
    private final EvdsProperties properties;
    private final EvdsMarketDataMapper evdsMarketDataMapper;
    private final SymbolNormalizer symbolNormalizer;

    public EvdsMarketDataProvider(EvdsClient evdsClient,
                                  EvdsProperties properties,
                                  EvdsMarketDataMapper evdsMarketDataMapper,
                                  SymbolNormalizer symbolNormalizer) {
        this.evdsClient = evdsClient;
        this.properties = properties;
        this.evdsMarketDataMapper = evdsMarketDataMapper;
        this.symbolNormalizer = symbolNormalizer;
    }

    @PostConstruct
    void logRegistration() {
        log.info(
                "EVDS provider bean initialized: enabled={}, seriesCount={}",
                properties.isEnabled(),
                properties.getSeries().size()
        );
    }

    @Override
    public DataSource source() {
        return DataSource.EVDS;
    }

    @Override
    public boolean supports(ProviderFetchRequest request) {
        boolean supported = properties.isEnabled()
                && (request == null || !request.hasSourceFilter() || request.source() == DataSource.EVDS);

        log.info(
                "EVDS provider supports evaluated: supported={}, enabled={}, requestSource={}",
                supported,
                properties.isEnabled(),
                request == null ? null : request.source()
        );
        return supported;
    }

    @Override
    public ProviderFetchResult fetch(ProviderFetchRequest request) {
        List<EvdsProperties.SeriesConfig> targetSeries = resolveSeries(request);
        if (targetSeries.isEmpty()) {
            log.info("EVDS provider fetch skipped: no matching configured series");
            return new ProviderFetchResult(List.of(), List.of());
        }

        List<String> seriesCodes = targetSeries.stream()
                .map(EvdsProperties.SeriesConfig::getApiCode)
                .toList();

        log.info(
                "EVDS provider fetch started: symbolCount={}, seriesCodes={}",
                targetSeries.size(),
                seriesCodes
        );

        EvdsResponse response = evdsClient.fetchSeries(
                seriesCodes,
                request == null ? null : request.from(),
                request == null ? null : request.to()
        );

        List<MarketQuote> quotes = evdsMarketDataMapper.toMarketQuotes(response, targetSeries);
        var historyRecords = evdsMarketDataMapper.toHistoryRecords(response, targetSeries);
        log.info(
                "EVDS provider fetch completed: quoteCount={}, historyRecordCount={}",
                quotes.size(),
                historyRecords.size()
        );
        return new ProviderFetchResult(quotes, historyRecords);
    }

    @Override
    public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
        return fetch(request).quotes();
    }

    private List<EvdsProperties.SeriesConfig> resolveSeries(ProviderFetchRequest request) {
        if (request == null || !request.hasSymbolFilter()) {
            return properties.getSeries();
        }

        Set<String> requestedSymbols = request.symbols().stream()
                .flatMap(symbol -> symbolNormalizer.normalize(symbol).stream())
                .collect(java.util.stream.Collectors.toSet());

        return properties.getSeries().stream()
                .filter(series -> symbolNormalizer.normalize(series.getSymbol())
                        .map(requestedSymbols::contains)
                        .orElse(false))
                .toList();
    }
}
