package com.emrehalli.financeportal.market.provider.index;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registry for BIST-focused stock index symbols.
 * Maps user-friendly instrument codes to Yahoo Finance query symbols.
 */
@Component
public class IndexSymbolRegistry {

    // instrument_code → yahoo_symbol
    private static final Map<String, String> CODE_TO_YAHOO = Map.of(
            "BIST100", "XU100.IS",
            "BIST30",  "XU030.IS",
            "BIST50",  "XU050.IS",
            "XBANK",   "XBANK.IS",
            "XUSIN",   "XUSIN.IS",
            "XUTEK",   "XUTEK.IS",
            "XHOLD",   "XHOLD.IS"
    );

    // yahoo_symbol → instrument_code
    private static final Map<String, String> YAHOO_TO_CODE = Map.of(
            "XU100.IS", "BIST100",
            "XU030.IS", "BIST30",
            "XU050.IS", "BIST50",
            "XBANK.IS", "XBANK",
            "XUSIN.IS", "XUSIN",
            "XUTEK.IS", "XUTEK",
            "XHOLD.IS", "XHOLD"
    );

    // instrument_code → display name
    private static final Map<String, String> CODE_TO_NAME = Map.of(
            "BIST100", "BIST 100",
            "BIST30",  "BIST 30",
            "BIST50",  "BIST 50",
            "XBANK",   "BIST Banka",
            "XUSIN",   "BIST Sınai",
            "XUTEK",   "BIST Teknoloji",
            "XHOLD",   "BIST Holding"
    );

    public List<String> getAllYahooSymbols() {
        return List.copyOf(CODE_TO_YAHOO.values());
    }

    public List<String> getAllCodes() {
        return List.copyOf(CODE_TO_YAHOO.keySet());
    }

    public String toInstrumentCode(String yahooSymbol) {
        return YAHOO_TO_CODE.get(yahooSymbol);
    }

    public String toYahooSymbol(String code) {
        return CODE_TO_YAHOO.get(code);
    }

    public String toDisplayName(String code) {
        return CODE_TO_NAME.getOrDefault(code, code);
    }
}
