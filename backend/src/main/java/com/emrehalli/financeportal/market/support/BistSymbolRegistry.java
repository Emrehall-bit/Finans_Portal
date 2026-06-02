package com.emrehalli.financeportal.market.support;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry for BIST stock symbols.
 */
@Component
public class BistSymbolRegistry {

    private static final List<String> BIST_30_SYMBOLS = List.of(
            "AKBNK", "AKGRT", "AKSA", "AKSEN", "ALARK", "ALBRK", "ALFAS", "ALKIM", "ANACM",
            "ARCLK", "ARDYZ", "ASELS", "ASTOR", "AYDEM", "BERA", "BIMAS", "BIRCH", "BOBET", "BRYAT",
            "BTCIM", "BUCIM", "CANTE", "CCOLA", "CEMTS", "CIMSA", "CLEBI", "CWENE", "DOAS", "DOHOL",
            "DYOBY", "ECILC", "EGEEN", "EKGYO", "ENERY", "ENJSA", "ENKAI", "ERBOS", "EREGL", "EUPWR",
            "FAVOR", "FENER", "FLAP", "FROTO", "GARAN", "GENIL", "GESAN", "GLYHO", "GMTAS", "GOODY",
            "GUBRF", "GWIND", "HALKB", "HEKTS", "HLGYO", "ISCTR", "ISGYO", "ISMEN", "IZENR", "JANTS",
            "KARSN", "KARTN", "KAYSE", "KCHOL", "KERVT", "KLGYO", "KNFRT", "KONYA", "KOZAA", "KOZAL",
            "KRDMD", "KRONT", "KTLEV", "LOGO", "MAVI", "MGROS", "MIATK", "MPARK", "NETAS", "NTTUR",
            "ODAS", "ONCSM", "ORGE", "OTKAR", "OYAKC", "PEHOL", "PETKM", "PGSUS", "QUAGR", "RAYSG",
            "SAHOL", "SASA", "SAYAS", "SISE", "SKBNK", "SNPAM", "SOKM", "SRVGY", "TAVHL", "TCELL",
            "THYAO", "TKFEN", "TOASO", "TRGYO", "TTKOM", "TTRAK", "TSKB", "TUPRS", "TURSG", "ULKER",
            "VAKBN", "VERUS", "VESTL", "YKBNK", "YYLGD", "ZOREN"
    );

    public List<String> getAllSymbols() {
        return BIST_30_SYMBOLS;
    }

    public List<String> getAllYahooSymbols() {
        return BIST_30_SYMBOLS.stream()
                .map(this::toYahooSymbol)
                .toList();
    }

    public String toYahooSymbol(String bistSymbol) {
        return bistSymbol + ".IS";
    }
}




