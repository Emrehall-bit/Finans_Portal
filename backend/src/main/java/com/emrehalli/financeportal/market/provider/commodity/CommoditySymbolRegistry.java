package com.emrehalli.financeportal.market.provider.commodity;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registry for commodity symbols fetched from Yahoo Finance.
 *
 * <p>BRENT is a public commodity exposed directly.
 * GOLD_USD and SILVER_USD are internal raw USD prices used to calculate
 * Turkish gold/silver instrument prices; they are not exposed to the API.</p>
 */
@Component
public class CommoditySymbolRegistry {

    // instrument_code â†’ yahoo_symbol (fetched from Yahoo)
    private static final Map<String, String> CODE_TO_YAHOO = Map.of(
            "BRENT",      "BZ=F",
            "GOLD_USD",   "GC=F",
            "SILVER_USD", "SI=F"
    );

    // yahoo_symbol â†’ instrument_code
    private static final Map<String, String> YAHOO_TO_CODE = Map.of(
            "BZ=F", "BRENT",
            "GC=F", "GOLD_USD",
            "SI=F", "SILVER_USD"
    );

    // display names: covers both Yahoo-direct and calculated instruments
    private static final Map<String, String> CODE_TO_NAME = Map.of(
            "BRENT",               "Brent Petrol",
            "GOLD_USD",            "AltÄ±n (Ham USD)",
            "SILVER_USD",          "GÃ¼mÃ¼ÅŸ (Ham USD)",
            "GRAM_ALTIN",          "Gram AltÄ±n",
            "CEYREK_ALTIN",        "Ã‡eyrek AltÄ±n",
            "YARIM_ALTIN",         "YarÄ±m AltÄ±n",
            "TAM_ALTIN",           "Tam AltÄ±n",
            "CUMHURIYET_ALTINI",   "Cumhuriyet AltÄ±nÄ±",
            "GUMUS_GRAM",          "GÃ¼mÃ¼ÅŸ Gram"
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

