package com.emrehalli.financeportal.admin.markettape.service;

import com.emrehalli.financeportal.admin.markettape.dto.MarketTapeConfigResponse;
import com.emrehalli.financeportal.admin.markettape.entity.MarketTapeSymbol;
import com.emrehalli.financeportal.admin.markettape.repository.MarketTapeSymbolRepository;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    public MarketTapeConfigResponse getConfig() {
        List<String> configuredSymbols = marketTapeSymbolRepository.findAllByEnabledTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(MarketTapeSymbol::getSymbol)
                .toList();

        if (configuredSymbols.isEmpty()) {
            return new MarketTapeConfigResponse(DEFAULT_SYMBOLS);
        }

        return new MarketTapeConfigResponse(configuredSymbols);
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
}



