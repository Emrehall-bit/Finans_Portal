package com.emrehalli.financeportal.market.provider.evdsmacro;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.evds.EvdsMarketDataMapper;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evdsmacro.config.EvdsMacroProperties;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.service.MarketBackfillStatusService;
import com.emrehalli.financeportal.market.service.MarketHistoryService;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class EvdsMacroDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(EvdsMacroDataProvider.class);
    private static final Pattern SERIES_FORMULA_PATTERN = Pattern.compile("^(.*)-(\\d+)$");
    private static final String HISTORY_RESET_MARKER_PREFIX = "EMR:";

    private final EvdsMacroProperties properties;
    private final EvdsMacroClient client;
    private final EvdsMarketDataMapper mapper;
    private final SymbolNormalizer symbolNormalizer;
    private final InstrumentRegistryService instrumentRegistryService;
    private final MarketHistoryService marketHistoryService;
    private final MarketBackfillStatusService marketBackfillStatusService;

    public EvdsMacroDataProvider(EvdsMacroProperties properties,
                                 EvdsMacroClient client,
                                 EvdsMarketDataMapper mapper,
                                 SymbolNormalizer symbolNormalizer,
                                 InstrumentRegistryService instrumentRegistryService,
                                 MarketHistoryService marketHistoryService,
                                 MarketBackfillStatusService marketBackfillStatusService) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.symbolNormalizer = symbolNormalizer;
        this.instrumentRegistryService = instrumentRegistryService;
        this.marketHistoryService = marketHistoryService;
        this.marketBackfillStatusService = marketBackfillStatusService;
    }

    @PostConstruct
    void logRegistration() {
        log.info("EVDS macro provider bean initialized: enabled={}", properties.isEnabled());
    }

    @Override
    public DataSource source() {
        return DataSource.EVDS_MACRO;
    }

    @Override
    public boolean supports(ProviderFetchRequest request) {
        boolean sourceSupported = request == null || !request.hasSourceFilter() || request.source() == DataSource.EVDS_MACRO;
        boolean typeSupported = request == null
                || !request.hasInstrumentTypeFilter()
                || request.instrumentTypes().contains(InstrumentType.MACRO_INDICATOR);
        return properties.isEnabled() && sourceSupported && typeSupported;
    }

    @Override
    public ProviderFetchResult fetch(ProviderFetchRequest request) {
        List<InstrumentRegistryService.ResolvedMapping> mappings = resolveMappings(request);
        if (mappings.isEmpty()) {
            log.info("EVDS macro provider fetch skipped: no matching macro mappings");
            return new ProviderFetchResult(List.of(), List.of());
        }

        log.info("EVDS macro provider fetch started: requestedSeriesCount={}", mappings.size());

        List<MarketQuote> quotes = new java.util.ArrayList<>();
        List<MarketHistoryRecord> historyRecords = new java.util.ArrayList<>();
        List<String> failedSymbols = new java.util.ArrayList<>();

        Map<String, List<ResolvedMacroRequest>> requestsByBaseSeries = mappings.stream()
                .map(mapping -> toResolvedMacroRequest(mapping))
                .collect(Collectors.groupingBy(
                        ResolvedMacroRequest::baseSeriesCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (List<ResolvedMacroRequest> group : requestsByBaseSeries.values()) {
            try {
                group.forEach(resolved -> ensureHistoryReset(resolved.mapping().symbol()));
                var response = client.fetchSeries(
                        group.stream()
                                .map(resolved -> new EvdsMacroClient.SeriesFormulaRequest(resolved.baseSeriesCode(), resolved.formula()))
                                .toList(),
                        request == null ? null : request.from(),
                        request == null ? null : request.to()
                );

                List<EvdsProperties.SeriesConfig> seriesConfigs = group.stream()
                        .map(resolved -> toSeriesConfig(resolved.mapping()))
                        .toList();
                quotes.addAll(remapQuotes(mapper.toMarketQuotes(response, seriesConfigs)));
                historyRecords.addAll(remapHistory(mapper.toHistoryRecords(response, seriesConfigs)));
            } catch (Exception ex) {
                failedSymbols.addAll(group.stream().map(resolved -> resolved.mapping().symbol()).toList());
                log.warn("EVDS macro fetch failed: symbols={}, providerSymbols={}, error={}",
                        group.stream().map(resolved -> resolved.mapping().symbol()).toList(),
                        group.stream().map(resolved -> resolved.mapping().providerSymbol()).toList(),
                        ex.getMessage(), ex);
            }
        }

        if (!failedSymbols.isEmpty()) {
            log.warn("EVDS macro provider partial failure: failedSymbols={}", failedSymbols);
        }

        log.info("EVDS macro provider fetch completed: source={}, requestedSeriesCount={}, savedQuoteCount={}, historyRecordCount={}, failedSeriesCount={}",
                DataSource.EVDS_MACRO, mappings.size(), quotes.size(), historyRecords.size(), failedSymbols.size());

        return new ProviderFetchResult(List.copyOf(quotes), List.copyOf(historyRecords));
    }

    @Override
    public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
        return fetch(request).quotes();
    }

    private List<InstrumentRegistryService.ResolvedMapping> resolveMappings(ProviderFetchRequest request) {
        InstrumentRegistryService.Resolution resolution = instrumentRegistryService.resolveMappings(DataSource.EVDS_MACRO);
        List<InstrumentRegistryService.ResolvedMapping> macroMappings = resolution.mappings().stream()
                .filter(mapping -> mapping.instrumentType() == InstrumentType.MACRO_INDICATOR)
                .toList();

        log.info("Market provider registry resolved: providerSource={}, registryMappingCount={}, resolvedSymbols={}",
                DataSource.EVDS_MACRO,
                macroMappings.size(),
                macroMappings.stream().map(InstrumentRegistryService.ResolvedMapping::providerSymbol).toList());

        if (request == null || !request.hasSymbolFilter()) {
            return macroMappings;
        }

        Set<String> requestedSymbols = request.symbols().stream()
                .flatMap(symbol -> symbolNormalizer.normalize(symbol).stream())
                .collect(Collectors.toSet());

        return macroMappings.stream()
                .filter(mapping -> requestedSymbols.contains(mapping.symbol()))
                .toList();
    }

    private EvdsProperties.SeriesConfig toSeriesConfig(InstrumentRegistryService.ResolvedMapping mapping) {
        EvdsProperties.SeriesConfig config = new EvdsProperties.SeriesConfig();
        config.setEvdsKey(mapping.providerSymbol());
        config.setApiCode(mapping.providerSymbol());
        config.setSymbol(mapping.symbol());
        config.setName(mapping.displayName());
        config.setInstrumentType(mapping.instrumentType());
        config.setCurrency(mapping.currency());
        return config;
    }

    private void ensureHistoryReset(String symbol) {
        String markerKey = HISTORY_RESET_MARKER_PREFIX + symbol;
        if (marketBackfillStatusService.hasCompletedOneTimeMarker(DataSource.EVDS_MACRO, markerKey)) {
            return;
        }

        long deletedCount = marketHistoryService.purgeHistoryForSymbol(symbol);
        marketBackfillStatusService.markCompletedOneTimeMarker(
                DataSource.EVDS_MACRO,
                markerKey,
                "Purged legacy market_history rows before first EVDS macro refresh. deletedCount=" + deletedCount
        );
        log.info("EVDS macro history reset completed: symbol={}, deletedCount={}", symbol, deletedCount);
    }

    private ResolvedMacroRequest toResolvedMacroRequest(InstrumentRegistryService.ResolvedMapping mapping) {
        MacroSeriesSpec spec = MacroSeriesSpec.fromProviderSymbol(mapping.providerSymbol());
        return new ResolvedMacroRequest(mapping, spec.baseSeriesCode(), spec.formula());
    }

    private List<MarketQuote> remapQuotes(List<MarketQuote> quotes) {
        return quotes.stream()
                .map(quote -> new MarketQuote(
                        quote.symbol(),
                        quote.displayName(),
                        quote.instrumentType(),
                        quote.price(),
                        quote.changeRate(),
                        quote.currency(),
                        DataSource.EVDS_MACRO,
                        quote.priceTime(),
                        quote.fetchedAt(),
                        quote.priceStatus()
                ))
                .toList();
    }

    private List<MarketHistoryRecord> remapHistory(List<MarketHistoryRecord> historyRecords) {
        return historyRecords.stream()
                .map(record -> new MarketHistoryRecord(
                        record.symbol(),
                        record.displayName(),
                        record.instrumentType(),
                        DataSource.EVDS_MACRO,
                        record.priceDate(),
                        record.closePrice(),
                        record.currency()
                ))
                .toList();
    }

    private record MacroSeriesSpec(String baseSeriesCode, int formula) {
        private static MacroSeriesSpec fromProviderSymbol(String providerSymbol) {
            if (providerSymbol == null || providerSymbol.isBlank()) {
                throw new IllegalArgumentException("Macro provider symbol cannot be blank");
            }

            Matcher matcher = SERIES_FORMULA_PATTERN.matcher(providerSymbol.trim().toUpperCase());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Macro provider symbol must include formula suffix: " + providerSymbol);
            }

            return new MacroSeriesSpec(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
    }

    private record ResolvedMacroRequest(
            InstrumentRegistryService.ResolvedMapping mapping,
            String baseSeriesCode,
            int formula
    ) {
    }
}
