package com.emrehalli.financeportal.market.provider.tefas;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.tefas.client.TefasClient;
import com.emrehalli.financeportal.market.provider.tefas.config.TefasProviderProperties;
import com.emrehalli.financeportal.market.provider.tefas.dto.TefasFundResponse;
import com.emrehalli.financeportal.market.provider.tefas.mapper.TefasMapper;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TefasMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TefasMarketDataProvider.class);

    private final TefasClient tefasClient;
    private final TefasProviderProperties properties;
    private final TefasMapper mapper;
    private final SymbolNormalizer symbolNormalizer;

    public TefasMarketDataProvider(TefasClient tefasClient,
                                   TefasProviderProperties properties,
                                   TefasMapper mapper,
                                   SymbolNormalizer symbolNormalizer) {
        this.tefasClient = tefasClient;
        this.properties = properties;
        this.mapper = mapper;
        this.symbolNormalizer = symbolNormalizer;
    }

    @Override
    public DataSource source() {
        return DataSource.TEFAS;
    }

    @Override
    public boolean supports(ProviderFetchRequest request) {
        return properties.isEnabled()
                && (request == null || !request.hasSourceFilter() || request.source() == DataSource.TEFAS)
                && (request == null || !request.hasInstrumentTypeFilter() || request.instrumentTypes().contains(InstrumentType.FUND));
    }

    @Override
    public ProviderFetchResult fetch(ProviderFetchRequest request) {
        List<String> symbols = resolveSymbols(request);
        if (symbols.isEmpty()) {
            log.info("TEFAS provider fetch skipped: no matching configured symbols");
            return new ProviderFetchResult(List.of(), List.of());
        }

        log.info("TEFAS provider fetch started: symbolCount={}", symbols.size());

        try {
            List<TefasFundResponse> responses = tefasClient.fetchFunds(symbols);
            List<MarketQuote> quotes = mapper.toMarketQuotes(responses);
            List<MarketHistoryRecord> historyRecords = includeHistory(request)
                    ? filterHistoryByRequest(mapper.toHistoryRecords(responses), request)
                    : List.of();

            log.info(
                    "TEFAS provider fetch completed: requestedSymbolCount={}, quoteCount={}, historyRecordCount={}",
                    symbols.size(),
                    quotes.size(),
                    historyRecords.size()
            );
            return new ProviderFetchResult(quotes, historyRecords);
        } catch (Exception ex) {
            log.warn("TEFAS provider fetch failed: error={}", ex.getMessage(), ex);
            return new ProviderFetchResult(List.of(), List.of());
        }
    }

    @Override
    public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
        return fetch(request).quotes();
    }

    private List<MarketHistoryRecord> filterHistoryByRequest(List<MarketHistoryRecord> records, ProviderFetchRequest request) {
        if (request == null || records.isEmpty()) {
            return records;
        }

        LocalDate from = request.from();
        LocalDate to = request.to();
        return records.stream()
                .filter(record -> from == null || !record.priceDate().isBefore(from))
                .filter(record -> to == null || !record.priceDate().isAfter(to))
                .toList();
    }

    private boolean includeHistory(ProviderFetchRequest request) {
        if (request == null) {
            return true;
        }

        if (request.from() == null && request.to() == null) {
            return true;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return (request.from() == null || !today.isBefore(request.from()))
                && (request.to() == null || !today.isAfter(request.to()));
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
