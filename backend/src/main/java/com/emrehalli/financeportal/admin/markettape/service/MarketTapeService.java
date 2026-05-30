package com.emrehalli.financeportal.admin.markettape.service;

import com.emrehalli.financeportal.admin.markettape.dto.MarketTapeConfigResponse;
import com.emrehalli.financeportal.admin.markettape.entity.MarketTapeSymbol;
import com.emrehalli.financeportal.admin.markettape.repository.MarketTapeSymbolRepository;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MarketTapeService {

    private static final List<String> DEFAULT_SYMBOLS = List.of(
            "XU100",
            "BIST100",
            "BTCUSDT",
            "BTCTRY",
            "BTC",
            "USDTRY",
            "EURTRY",
            "XAUTRY",
            "GRAMALTIN",
            "ETHUSDT",
            "ETHTRY",
            "ETH"
    );

    private final MarketTapeSymbolRepository marketTapeSymbolRepository;
    private final MarketQueryService marketQueryService;

    public MarketTapeConfigResponse getConfig() {
        List<String> configuredSymbols = marketTapeSymbolRepository.findAllByEnabledTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(MarketTapeSymbol::getSymbol)
                .toList();

        if (configuredSymbols.isEmpty()) {
            return new MarketTapeConfigResponse(DEFAULT_SYMBOLS);
        }

        return new MarketTapeConfigResponse(configuredSymbols);
    }

    public List<MarketQueryService.MarketSnapshot> getQuotes() {
        List<String> configuredSymbols = getConfig().symbols();
        Map<String, MarketQueryService.MarketSnapshot> quotes = new LinkedHashMap<>();

        for (String symbol : configuredSymbols) {
            resolveQuote(symbol).ifPresent(quote -> quotes.putIfAbsent(uniqueKey(quote), quote));
        }

        return List.copyOf(quotes.values());
    }

    @Transactional
    public MarketTapeConfigResponse updateConfig(List<String> symbols) {
        List<String> normalizedSymbols = normalizeSymbols(symbols);
        if (normalizedSymbols.isEmpty()) {
            throw new BadRequestException("At least one market tape symbol is required");
        }

        marketTapeSymbolRepository.deleteAllInBatch();

        List<MarketTapeSymbol> records = new ArrayList<>();
        for (int index = 0; index < normalizedSymbols.size(); index++) {
            MarketTapeSymbol record = new MarketTapeSymbol();
            record.setSymbol(normalizedSymbols.get(index));
            record.setDisplayOrder(index);
            record.setEnabled(Boolean.TRUE);
            records.add(record);
        }

        marketTapeSymbolRepository.saveAll(records);
        return new MarketTapeConfigResponse(normalizedSymbols);
    }

    private List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            return List.of();
        }

        Set<String> uniqueSymbols = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (symbol == null) {
                continue;
            }

            String normalized = symbol.trim().toUpperCase();
            if (!normalized.isBlank()) {
                uniqueSymbols.add(normalized);
            }
        }

        return List.copyOf(uniqueSymbols);
    }

    private Optional<MarketQueryService.MarketSnapshot> resolveQuote(String symbol) {
        for (SymbolCandidate candidate : buildCandidates(symbol)) {
            Optional<MarketQueryService.MarketSnapshot> quote = marketQueryService.findBySymbol(candidate.symbol(), candidate.type());
            if (quote.isPresent() && quote.get().price() != null) {
                return quote;
            }
        }
        return Optional.empty();
    }

    private List<SymbolCandidate> buildCandidates(String symbol) {
        String normalized = normalizeLookupSymbol(symbol);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<SymbolCandidate> candidates = new ArrayList<>();
        addCandidate(candidates, normalized, null);

        switch (normalized) {
            case "XU100" -> addCandidate(candidates, "BIST100", InstrumentType.INDEX);
            case "XU030" -> addCandidate(candidates, "BIST30", InstrumentType.INDEX);
            case "XU050" -> addCandidate(candidates, "BIST50", InstrumentType.INDEX);
            case "XAUTRY", "GRAMALTIN", "GRAM_ALTIN" -> addCandidate(candidates, "GRAM_ALTIN", InstrumentType.COMMODITY);
            case "GUMUSTRY", "GUMUSGRAM", "GUMUS_GRAM" -> addCandidate(candidates, "GUMUS_GRAM", InstrumentType.COMMODITY);
            case "BTCUSDT", "BTCTRY" -> addCandidate(candidates, "BTC", InstrumentType.CRYPTO);
            case "ETHUSDT", "ETHTRY" -> addCandidate(candidates, "ETH", InstrumentType.CRYPTO);
            case "USDTRY" -> addCandidate(candidates, "TCMB:USD:SELL", InstrumentType.FX);
            case "EURTRY" -> addCandidate(candidates, "TCMB:EUR:SELL", InstrumentType.FX);
            default -> {
                if (normalized.endsWith("TRY") && normalized.length() > 3) {
                    addCandidate(candidates, normalized.substring(0, normalized.length() - 3), InstrumentType.CRYPTO);
                }
            }
        }

        return candidates;
    }

    private void addCandidate(List<SymbolCandidate> candidates, String symbol, InstrumentType type) {
        SymbolCandidate candidate = new SymbolCandidate(symbol, type);
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private String normalizeLookupSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_:]", "");
    }

    private String uniqueKey(MarketQueryService.MarketSnapshot quote) {
        return quote.instrumentType() + ":" + quote.symbol();
    }

    private record SymbolCandidate(String symbol, InstrumentType type) {
    }
}




