package com.emrehalli.financeportal.market.provider.commodity;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registry for commodity symbols.
 * Maps user-friendly instrument codes to Yahoo Finance query symbols.
 */
@Component
public class CommoditySymbolRegistry {

    // instrument_code → yahoo_symbol
    private static final Map<String, String> CODE_TO_YAHOO = Map.of(
            "GOLD",   "GC=F",
            "SILVER", "SI=F",
            "BRENT",  "BZ=F",
            "WTI",    "CL=F",
            "NATGAS", "NG=F"
    );

    // yahoo_symbol → instrument_code
    private static final Map<String, String> YAHOO_TO_CODE = Map.of(
            "GC=F", "GOLD",
            "SI=F", "SILVER",
            "BZ=F", "BRENT",
            "CL=F", "WTI",
            "NG=F", "NATGAS"
    );

    // instrument_code → display name
    private static final Map<String, String> CODE_TO_NAME = Map.of(
            "GOLD",   "Altın (Vadeli)",
            "SILVER", "Gümüş (Vadeli)",
            "BRENT",  "Brent Petrol",
            "WTI",    "Ham Petrol",
            "NATGAS", "Doğalgaz"
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
