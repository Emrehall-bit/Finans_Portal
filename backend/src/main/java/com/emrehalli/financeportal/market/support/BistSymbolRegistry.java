package com.emrehalli.financeportal.market.support;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry for BIST stock symbols.
 */
@Component
public class BistSymbolRegistry {

    private static final List<String> BIST_30_SYMBOLS = List.of(
            "AEFES", "AGHOL", "AKBNK", "AKCNS", "AKFGY", "AKFYE", "AKGRT", "AKMGY", "AKSA", "AKSEN",
            "AKSUE", "ALARK", "ALBRK", "ALCTL", "ALFAS", "ALKIM", "ANACM", "ANHYT", "ARASE", "ARCLK",
            "ARDYZ", "ASELS", "ASTOR", "ASUZU", "AVPGY", "AYDEM", "BASGZ", "BEGYO", "BERA", "BFREN",
            "BIGCH", "BIMAS", "BIOEN", "BIRCH", "BOBET", "BRISA", "BRYAT", "BSOKE", "BTCIM", "BUCIM",
            "CANTE", "CATES", "CCOLA", "CEMTS", "CIMSA", "CLEBI", "CWENE", "DESA", "DESPC", "DEVA",
            "DGATE", "DOAS", "DOHOL",
            "DSTKF",
            "DYOBY", "ECILC", "EGEEN", "EKGYO", "ENERY", "ENJSA", "ENKAI", "ENTRA", "ERBOS", "EREGL",
            "EUPWR", "FAVOR", "FENER", "FLAP", "FORTE", "FROTO", "GARAN", "GENIL", "GESAN", "GLYHO",
            "GMTAS", "GOODY", "GRSEL", "GUBRF", "GWIND", "HALKB", "HEKTS", "HLGYO", "INDES", "INFO",
            "INVEO", "ISCTR", "ISDMR", "ISFIN", "ISGYO", "ISMEN", "ISYAT", "IZENR", "JANTS", "KAREL",
            "KARSN", "KARTN", "KAYSE", "KCHOL", "KERVT", "KLGYO", "KLSER", "KMPUR", "KNFRT", "KONYA",
            "KOZAA", "KOZAL", "KORDS", "KOTON", "KRDMD", "KRONT", "KRVGD", "KTLEV", "LOGO", "MAVI",
            "MGROS", "MIATK", "MPARK", "NETAS", "NTHOL", "NTTUR",
            "ODAS", "ONCSM", "ORGE", "OTKAR", "OYAKC", "PEHOL", "PENTA", "PETKM", "PGSUS", "QUAGR", "RAYSG",
            "SAHOL", "SASA", "SAYAS", "SISE", "SKBNK", "SNPAM", "SOKM", "SRVGY", "TAVHL", "TCELL",
            "THYAO", "TKFEN", "TOASO", "TRALT", "TRGYO", "TTKOM", "TTRAK", "TSKB", "TUPRS", "TURSG", "ULKER",
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

