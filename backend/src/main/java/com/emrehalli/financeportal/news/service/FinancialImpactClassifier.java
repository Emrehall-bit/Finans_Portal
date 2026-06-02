package com.emrehalli.financeportal.news.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

/**
 * Deterministic news classifier that decides whether a news item has financial/market impact
 * and categorises it by impact type. No AI/LLM calls â€” signal groups + compound rules only.
 *
 * <p>Scoring is additive (one point per signal group hit), capped per group to prevent
 * any single over-represented keyword dominating.  Impact type is determined by
 * priority-ordered threshold checks on each group's subtotal.
 */
@Component
public class FinancialImpactClassifier {

    private static final Logger logger = LogManager.getLogger(FinancialImpactClassifier.class);

    // --- Score thresholds ---
    private static final int THRESHOLD_MARKET_RELEVANT = 18;
    private static final int THRESHOLD_HIGH_CONFIDENCE = 50;

    // --- Impact type thresholds (per-group subtotals) ---
    private static final int THRESHOLD_MONETARY_POLICY_CB = 25;   // any central bank mention
    private static final int THRESHOLD_MONETARY_POLICY_COMBINED = 35; // CB + rate combined
    private static final int THRESHOLD_FISCAL = 25;
    private static final int THRESHOLD_POLITICAL_RISK = 25;        // compound score
    private static final int THRESHOLD_GEOPOLITICAL = 20;          // compound score
    private static final int THRESHOLD_MACRO = 18;
    private static final int THRESHOLD_GLOBAL_MARKET_COMBINED = 30; // global + market
    private static final int THRESHOLD_REGULATORY = 25;
    private static final int THRESHOLD_SECTOR = 18;

    // =====================================================================
    // Signal groups â€” checked via text.contains(token) on normalised text
    // =====================================================================

    /** Weight 30 each, cap 60. */
    private static final List<String> CENTRAL_BANK_TOKENS = List.of(
            "TCMB", "MERKEZ BANKASI", "TURKIYE CUMHURIYET MERKEZ BANKASI",
            "FED", "FEDERAL RESERVE", "FEDERAL REZERV",
            "ECB", "EUROPEAN CENTRAL BANK", "AVRUPA MERKEZ BANKASI",
            "FOMC", "PPK", "PARA POLITIKASI KURULU"
    );
    private static final int CENTRAL_BANK_WEIGHT = 30;
    private static final int CENTRAL_BANK_CAP = 60;

    /** Weight 20 each, cap 40. */
    private static final List<String> RATE_TOKENS = List.of(
            "POLITIKA FAIZI", "FAIZ ORANI", "REPO FAIZI",
            "FAIZ", "TAHVIL", "BONO", "GETIRI", "REPO", "SWAP"
    );
    private static final int RATE_WEIGHT = 20;
    private static final int RATE_CAP = 40;

    /** Weight 18 each, cap 36. */
    private static final List<String> FX_TOKENS = List.of(
            "USDTRY", "EURTRY", "TL KURU", "KUR BASKISI",
            "KUR", "DOVIZ", "DOLAR", "EURO", "PARITE",
            "USD", "EUR", "STERLING"
    );
    private static final int FX_WEIGHT = 18;
    private static final int FX_CAP = 36;

    /** Weight 15 each, cap 30. */
    private static final List<String> MARKET_TOKENS = List.of(
            "BIST 100", "BIST100", "XU100",
            "HALKA ARZ", "SERMAYE PIYASASI",
            "BORSA", "BIST", "ENDEKS", "HISSE SENEDI",
            "PIYASALAR"
    );
    private static final int MARKET_WEIGHT = 15;
    private static final int MARKET_CAP = 30;

    /** Weight 18 each, cap 36. */
    private static final List<String> MACRO_TOKENS = List.of(
            "ENFLASYON", "TUFE", "GSYH", "GDP",
            "CARI ACIK", "CARI DENGE", "TICARET DENGESI",
            "BUYUME", "ISSIZLIK", "ENFLASYON RAKAMLARI"
    );
    private static final int MACRO_WEIGHT = 18;
    private static final int MACRO_CAP = 36;

    /** Weight 18 each, cap 36. */
    private static final List<String> FISCAL_TOKENS = List.of(
            "HAZINE BONOSU", "DEVLET TAHVILI", "BUTCE DENGESI",
            "KAMU HARCAMA", "HAZINE BAKANLIGI",
            "HAZINE", "BUTCE", "BORCLANMA", "EUROBOND", "MALIYE", "OVP"
    );
    private static final int FISCAL_WEIGHT = 18;
    private static final int FISCAL_CAP = 36;

    /** Weight 18 each, cap 36. */
    private static final List<String> GOLD_TOKENS = List.of(
            "ONS ALTIN", "ALTIN FIYATI", "GRAM ALTIN", "XAUUSD",
            "ALTIN", "ONS"
    );
    private static final int GOLD_WEIGHT = 18;
    private static final int GOLD_CAP = 36;

    /** Weight 18 each, cap 36. */
    private static final List<String> OIL_TOKENS = List.of(
            "HAM PETROL", "PETROL FIYATI", "DOGAL GAZ",
            "PETROL", "BRENT", "OPEC", "AKARYAKIT"
    );
    private static final int OIL_WEIGHT = 18;
    private static final int OIL_CAP = 36;

    /** Weight 18 each, cap 36. */
    private static final List<String> BANKING_TOKENS = List.of(
            "BANKACILIK SEKTORU", "KREDI BUYUMESI", "MEVDUAT FAIZI",
            "BDDK", "BANKACILIK", "MEVDUAT", "XBANK"
    );
    private static final int BANKING_WEIGHT = 18;
    private static final int BANKING_CAP = 36;

    /** Weight 18 each, cap 36. */
    private static final List<String> REGULATORY_TOKENS = List.of(
            "REKABET KURUMU", "SERMAYE PIYASASI KURULU",
            "SPK", "EPDK", "DUZENLEYICI", "LISANS IPTALI", "YAPTIRIM UYGULANDI"
    );
    private static final int REGULATORY_WEIGHT = 18;
    private static final int REGULATORY_CAP = 36;

    /** Weight 15 each, cap 30. */
    private static final List<String> GLOBAL_MARKET_TOKENS = List.of(
            "KURESEL PIYASALAR", "KURESEL PIYASA", "KURESEL DALGALANMA",
            "DOW JONES", "NASDAQ", "S&P 500", "WALLSTREET",
            "FTSE", "DAX", "NIKKEI"
    );
    private static final int GLOBAL_WEIGHT = 15;
    private static final int GLOBAL_CAP = 30;

    // Short tokens that cause substring false positives on Turkish text (e.g. "KUR" in "küresel")
    private static final Set<String> WORD_BOUNDARY_REQUIRED = Set.of(
            "KUR", "TL", "FED", "ECB", "PPK", "EUR", "USD", "TRY"
    );

    // --- Compound rule support ---

    /** Political tokens (alone are weak; need market context to qualify). */
    private static final List<String> POLITICAL_TOKENS = List.of(
            "SIYASI BELIRSIZLIK", "HUKUMET KRIZI", "ERKEN SECIM", "SECIM SONUCLARI",
            "SECIM", "PARTI", "KURULTAY", "YSK", "REFERANDUM", "KOALISYON"
    );

    /** Geopolitical tokens (alone are weak; need commodity/FX context). */
    private static final List<String> GEOPOLITICAL_TOKENS = List.of(
            "BÃ–LGESEL GERILIM", "ASKERI OPERASYON",
            "SAVAS", "CATISMA", "AMBARGO", "YAPTIRIM", "NUKLEER", "ATESKES"
    );

    /** Context tokens that turn political mentions into POLITICAL_RISK. */
    private static final List<String> MARKET_CONTEXT_TOKENS = List.of(
            "BIST 100", "BIST", "XU100", "PIYASALAR", "PIYASA",
            "BORSA", "KUR", "DOVIZ", "DOLAR", "TL", "KRIZ", "BELIRSIZLIK"
    );

    // =====================================================================
    // Public API
    // =====================================================================

    /**
     * Classifies a news item for financial/market impact.
     *
     * @param title      news headline
     * @param summary    body or summary text
     * @param content    optional full article content (may be null)
     * @param provider   provider name (e.g. "KAP", "AA_RSS")
     * @param feedName   optional feed name (e.g. "economy"), may be null
     * @param category   news category string
     * @param sourceUrl  source URL (used for context only)
     */
    public FinancialImpactResult classify(
            String title,
            String summary,
            String content,
            String provider,
            String feedName,
            String category,
            String sourceUrl
    ) {
        // KAP disclosures: always market-relevant by definition
        if ("KAP".equalsIgnoreCase(provider) || "KAP_RSS".equalsIgnoreCase(provider)) {
            return FinancialImpactResult.ofKap();
        }

        String text = normalizeText(title, summary, content, category);
        if (text.isBlank()) {
            return FinancialImpactResult.notRelevant("BoÅŸ metin");
        }

        List<String> matched = new ArrayList<>();

        // --- Group scores ---
        int centralBankScore = scoreGroup(text, CENTRAL_BANK_TOKENS, CENTRAL_BANK_WEIGHT, CENTRAL_BANK_CAP, matched);
        int rateScore        = scoreGroup(text, RATE_TOKENS,         RATE_WEIGHT,         RATE_CAP,         matched);
        int fxScore          = scoreGroup(text, FX_TOKENS,           FX_WEIGHT,           FX_CAP,           matched);
        int marketScore      = scoreGroup(text, MARKET_TOKENS,       MARKET_WEIGHT,       MARKET_CAP,       matched);
        int macroScore       = scoreGroup(text, MACRO_TOKENS,        MACRO_WEIGHT,        MACRO_CAP,        matched);
        int fiscalScore      = scoreGroup(text, FISCAL_TOKENS,       FISCAL_WEIGHT,       FISCAL_CAP,       matched);
        int goldScore        = scoreGroup(text, GOLD_TOKENS,         GOLD_WEIGHT,         GOLD_CAP,         matched);
        int oilScore         = scoreGroup(text, OIL_TOKENS,          OIL_WEIGHT,          OIL_CAP,          matched);
        int bankingScore     = scoreGroup(text, BANKING_TOKENS,      BANKING_WEIGHT,      BANKING_CAP,      matched);
        int regulatoryScore  = scoreGroup(text, REGULATORY_TOKENS,   REGULATORY_WEIGHT,   REGULATORY_CAP,   matched);
        int globalScore      = scoreGroup(text, GLOBAL_MARKET_TOKENS, GLOBAL_WEIGHT,      GLOBAL_CAP,       matched);

        // --- Compound: political risk (requires market context) ---
        int politicalScore = 0;
        boolean hasPoliticalToken = POLITICAL_TOKENS.stream().anyMatch(text::contains);
        if (hasPoliticalToken) {
            addMatchedFromList(text, POLITICAL_TOKENS, matched);
            boolean hasMarketContext = MARKET_CONTEXT_TOKENS.stream().anyMatch(t -> containsToken(text, t));
            politicalScore = hasMarketContext ? 30 : 5;
        }

        // --- Compound: geopolitical (requires commodity/FX context) ---
        int geopoliticalScore = 0;
        boolean hasGeoToken = GEOPOLITICAL_TOKENS.stream().anyMatch(text::contains);
        if (hasGeoToken) {
            addMatchedFromList(text, GEOPOLITICAL_TOKENS, matched);
            if (goldScore > 0 || oilScore > 0 || fxScore >= 18) {
                geopoliticalScore = 25;
            } else if (marketScore > 0) {
                geopoliticalScore = 15;
            } else {
                geopoliticalScore = 5;
            }
        }

        // --- Direct company match (BIST aliases) ---
        int directCompanyScore = 0;
        List<String> directSymbols = new ArrayList<>();
        for (NewsService.InstrumentAlias alias : NewsService.BIST_INSTRUMENT_ALIASES.values()) {
            if (!"STOCK".equalsIgnoreCase(alias.instrumentType())) continue;
            for (String keyword : alias.keywords()) {
                String normalizedKw = normalizeText(keyword);
                if (!normalizedKw.isBlank() && text.contains(normalizedKw)) {
                    directCompanyScore = 35;
                    directSymbols.add(alias.symbol());
                    matched.add(alias.symbol());
                    break;
                }
            }
        }

        // --- Total score ---
        int totalScore = centralBankScore + rateScore + fxScore + marketScore + macroScore
                + fiscalScore + goldScore + oilScore + bankingScore + regulatoryScore
                + globalScore + politicalScore + geopoliticalScore + directCompanyScore;

        // --- Impact type (priority order) ---
        ImpactType impactType;
        List<AffectedAssetClass> assetClasses;
        String reason;

        if (directCompanyScore > 0) {
            impactType = ImpactType.DIRECT_COMPANY;
            assetClasses = List.of(AffectedAssetClass.STOCK);
            reason = "Haberde ÅŸirket adÄ±/tiker doÄŸrudan geÃ§iyor: " + directSymbols;

        } else if (centralBankScore >= THRESHOLD_MONETARY_POLICY_CB
                || (centralBankScore + rateScore) >= THRESHOLD_MONETARY_POLICY_COMBINED) {
            impactType = ImpactType.MONETARY_POLICY;
            assetClasses = List.of(AffectedAssetClass.FX, AffectedAssetClass.EQUITY_INDEX,
                    AffectedAssetClass.BANKING_INDEX, AffectedAssetClass.INTEREST_RATE);
            reason = "Merkez bankasÄ±/faiz sinyalleri para politikasÄ± etkisi gÃ¶steriyor";

        } else if (fiscalScore >= THRESHOLD_FISCAL) {
            impactType = ImpactType.FISCAL_POLICY;
            assetClasses = List.of(AffectedAssetClass.FX, AffectedAssetClass.EQUITY_INDEX, AffectedAssetClass.BOND);
            reason = "Hazine/bÃ¼tÃ§e sinyalleri mali politika etkisi gÃ¶steriyor";

        } else if (politicalScore >= THRESHOLD_POLITICAL_RISK) {
            impactType = ImpactType.POLITICAL_RISK;
            assetClasses = List.of(AffectedAssetClass.FX, AffectedAssetClass.EQUITY_INDEX, AffectedAssetClass.GOLD);
            reason = "Siyasi sinyal + piyasa baÄŸlamÄ± siyasi risk olarak sÄ±nÄ±flandÄ±rÄ±ldÄ±";

        } else if (geopoliticalScore >= THRESHOLD_GEOPOLITICAL) {
            impactType = ImpactType.GEOPOLITICAL;
            assetClasses = List.of(AffectedAssetClass.FX, AffectedAssetClass.EQUITY_INDEX,
                    AffectedAssetClass.GOLD, AffectedAssetClass.COMMODITY);
            reason = "Jeopolitik sinyal + emtia/kur baÄŸlamÄ± jeopolitik risk olarak sÄ±nÄ±flandÄ±rÄ±ldÄ±";

        } else if (macroScore >= THRESHOLD_MACRO) {
            impactType = ImpactType.MACRO;
            assetClasses = List.of(AffectedAssetClass.FX, AffectedAssetClass.EQUITY_INDEX);
            reason = "Makroekonomik sinyaller tespit edildi";

        } else if ((globalScore + marketScore) >= THRESHOLD_GLOBAL_MARKET_COMBINED) {
            impactType = ImpactType.GLOBAL_MARKET;
            assetClasses = List.of(AffectedAssetClass.EQUITY_INDEX, AffectedAssetClass.FX,
                    AffectedAssetClass.GOLD, AffectedAssetClass.COMMODITY);
            reason = "KÃ¼resel piyasa sinyalleri tespit edildi";

        } else if (regulatoryScore >= THRESHOLD_REGULATORY) {
            impactType = ImpactType.REGULATORY;
            assetClasses = List.of(AffectedAssetClass.EQUITY_INDEX);
            reason = "DÃ¼zenleyici kurul kararÄ± sinyali tespit edildi";

        } else if (bankingScore >= THRESHOLD_SECTOR) {
            impactType = ImpactType.SECTOR;
            assetClasses = List.of(AffectedAssetClass.BANKING_INDEX, AffectedAssetClass.EQUITY_INDEX);
            reason = "BankacÄ±lÄ±k sektÃ¶rÃ¼ sinyali tespit edildi";

        } else if (goldScore >= THRESHOLD_SECTOR || oilScore >= THRESHOLD_SECTOR) {
            impactType = ImpactType.GLOBAL_MARKET;
            assetClasses = goldScore >= oilScore
                    ? List.of(AffectedAssetClass.GOLD, AffectedAssetClass.COMMODITY)
                    : List.of(AffectedAssetClass.COMMODITY);
            reason = "Emtia sinyali tespit edildi";

        } else if (totalScore >= THRESHOLD_MARKET_RELEVANT) {
            // Catch-all: financially relevant but no dominant type
            if (fxScore >= 18) {
                impactType = ImpactType.MACRO;
                assetClasses = List.of(AffectedAssetClass.FX, AffectedAssetClass.EQUITY_INDEX);
                reason = "DÃ¶viz/kur sinyali piyasa etkisi gÃ¶steriyor";
            } else if (rateScore >= 20) {
                impactType = ImpactType.MACRO;
                assetClasses = List.of(AffectedAssetClass.INTEREST_RATE, AffectedAssetClass.BOND);
                reason = "Faiz/tahvil sinyali piyasa etkisi gÃ¶steriyor";
            } else if (marketScore >= 20) {
                impactType = ImpactType.SECTOR;
                assetClasses = List.of(AffectedAssetClass.EQUITY_INDEX);
                reason = "Borsa/endeks sinyali piyasa etkisi gÃ¶steriyor";
            } else {
                impactType = ImpactType.MACRO;
                assetClasses = List.of(AffectedAssetClass.EQUITY_INDEX);
                reason = "Birden fazla piyasa sinyali tespit edildi";
            }
        } else {
            impactType = ImpactType.NOT_MARKET_RELEVANT;
            assetClasses = List.of(AffectedAssetClass.NONE);
            reason = "Yeterli finansal/piyasa sinyali bulunamadÄ± (score=" + totalScore + ")";
        }

        // --- Confidence + marketRelevant ---
        boolean marketRelevant;
        String confidence;
        if (impactType == ImpactType.NOT_MARKET_RELEVANT || totalScore < THRESHOLD_MARKET_RELEVANT) {
            impactType = ImpactType.NOT_MARKET_RELEVANT;
            assetClasses = List.of(AffectedAssetClass.NONE);
            confidence = "LOW";
            marketRelevant = false;
        } else if (impactType == ImpactType.DIRECT_COMPANY || totalScore >= THRESHOLD_HIGH_CONFIDENCE) {
            confidence = "HIGH";
            marketRelevant = true;
        } else {
            confidence = "MEDIUM";
            marketRelevant = true;
        }

        List<String> dedupedSignals = deduplicate(matched);
        FinancialImpactResult result = new FinancialImpactResult(
                marketRelevant, confidence, impactType, assetClasses,
                totalScore, reason, dedupedSignals);

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "news.classify: provider={}, feed={}, title=\"{}\", score={}, marketRelevant={}, " +
                    "confidence={}, impactType={}, affectedAssetClasses={}, matchedSignals={}, reason=\"{}\"",
                    provider, feedName, truncate(title, 80),
                    totalScore, marketRelevant, confidence, impactType, assetClasses,
                    dedupedSignals, reason);
        }

        return result;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private int scoreGroup(String text, List<String> tokens, int weight, int cap, List<String> matched) {
        int total = 0;
        for (String token : tokens) {
            if (containsToken(text, token)) {
                total += weight;
                matched.add(token);
            }
        }
        return Math.min(total, cap);
    }

    private boolean containsToken(String text, String token) {
        if (WORD_BOUNDARY_REQUIRED.contains(token)) {
            return (" " + text + " ").contains(" " + token + " ");
        }
        return text.contains(token);
    }

    private void addMatchedFromList(String text, List<String> tokens, List<String> matched) {
        for (String token : tokens) {
            if (text.contains(token)) {
                matched.add(token);
            }
        }
    }

    private String normalizeText(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                sb.append(' ').append(part);
            }
        }
        String combined = sb.toString();
        String normalized = Normalizer.normalize(combined, Normalizer.Form.NFD)
                .replace('\u0131', 'i')
                .replace('\u0130', 'I')
                .replaceAll("\\p{M}+", "");
        return normalized.toUpperCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> deduplicate(List<String> input) {
        Set<String> seen = new LinkedHashSet<>(input);
        return List.copyOf(seen);
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "â€¦";
    }
}




