package com.emrehalli.financeportal.market.support;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Single source of truth for whitelisted crypto base assets.
 * Only these base assets are eligible for Binance TRY pair discovery, price fetch, and history backfill.
 */
@Component
public class CryptoWhitelist {

    static final Set<String> WHITELIST = Set.of(
            "BTC", "ETH", "USDT", "BNB", "XRP", "SOL", "USDC", "TRX", "DOGE", "ADA",
            "HYPE", "BCH", "LINK", "LEO", "XLM", "AVAX", "TON", "SUI", "SHIB", "LTC",
            "DOT", "HBAR", "UNI", "DAI", "PEPE", "PI", "AAVE", "NEAR", "APT", "ETC",
            "ONDO", "ICP", "OKB", "CRO", "ATOM", "KAS", "POL", "ARB", "VET", "ALGO",
            "FIL", "RENDER", "FET", "WLD", "SEI", "OP", "INJ", "QNT", "XMR", "STX",
            "TIA", "IMX", "MKR", "GRT", "THETA", "JUP", "LDO", "JASMY", "RUNE", "PYTH",
            "FLOW", "SAND", "GALA", "MANA", "AXS", "CHZ", "APE", "ENA", "PENDLE", "CRV",
            "COMP", "SNX", "DYDX", "1INCH", "ZEC", "DASH", "EGLD", "KAVA", "MINA", "ROSE",
            "CELO", "GMT", "ZIL", "BAT", "QTUM", "IOTA", "NEO", "KSM", "LRC", "ENS",
            "MASK", "API3", "BLUR", "ID", "ARKM", "ALT", "STRK", "AEVO", "MEME", "NOT",
            "WIF", "BONK", "FLOKI", "PENGU", "TURBO", "PNUT", "ORDI", "SATS", "BOME", "PEOPLE",
            "TWT", "CAKE", "RAY", "JTO", "WOO", "ZRX", "SUSHI", "YFI", "GMX", "CVX",
            "BAL", "UMA", "BAND", "SKL", "ANKR", "COTI", "CTSI", "OCEAN", "LPT", "AUDIO",
            "ARPA", "IOST", "ONE", "HIVE", "XTZ", "EOS", "XEC", "CFX", "FTM", "LUNC",
            "LUNA", "KDA", "DCR", "SC", "RVN", "GLM", "ELF", "TFUEL", "FIDA", "MAGIC",
            // Ek coinler
            "TAO", "VIRTUAL", "AIXBT", "GRASS", "ZRO", "ZK", "LISTA", "REZ", "ETHFI", "PORTAL",
            "DYM", "OMNI", "MANTA", "PIXEL", "SAGA", "LAYER", "MOVE", "EIGEN", "DRIFT", "KMNO",
            "BRETT"
    );

    public boolean isWhitelisted(String baseAsset) {
        if (baseAsset == null || baseAsset.isBlank()) {
            return false;
        }
        return WHITELIST.contains(baseAsset.trim().toUpperCase());
    }

    public Set<String> getAll() {
        return WHITELIST;
    }
}
