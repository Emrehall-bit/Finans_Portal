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

    // instrument_code → yahoo_symbol (fetched from Yahoo)
    private static final Map<String, String> CODE_TO_YAHOO = Map.of(
            "BRENT",      "BZ=F",
            "GOLD_USD",   "GC=F",
            "SILVER_USD", "SI=F"
    );

    // yahoo_symbol → instrument_code
    private static final Map<String, String> YAHOO_TO_CODE = Map.of(
            "BZ=F", "BRENT",
            "GC=F", "GOLD_USD",
            "SI=F", "SILVER_USD"
    );

    // display names: covers both Yahoo-direct and calculated instruments
    private static final Map<String, String> CODE_TO_NAME = Map.of(
            "BRENT",               "Brent Petrol",
            "GOLD_USD",            "Altın (Ham USD)",
            "SILVER_USD",          "Gümüş (Ham USD)",
            "GRAM_ALTIN",          "Gram Altın",
            "CEYREK_ALTIN",        "Çeyrek Altın",
            "YARIM_ALTIN",         "Yarım Altın",
            "TAM_ALTIN",           "Tam Altın",
            "CUMHURIYET_ALTINI",   "Cumhuriyet Altını",
            "GUMUS_GRAM",          "Gümüş Gram"
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



