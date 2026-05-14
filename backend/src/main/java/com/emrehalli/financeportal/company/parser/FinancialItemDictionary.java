package com.emrehalli.financeportal.company.parser;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FinancialItemDictionary {

    private static final Map<FinancialItemKey, List<String>> ALIASES = new EnumMap<>(FinancialItemKey.class);

    static {
        ALIASES.put(FinancialItemKey.HASILAT, List.of(
                "Hasılat", "Satış Gelirleri", "Net Satışlar", "Revenue"
        ));
        ALIASES.put(FinancialItemKey.BRUT_KAR, List.of(
                "Brüt Kâr", "Brüt Kar", "Gross Profit"
        ));
        ALIASES.put(FinancialItemKey.ESAS_FAALIYET_KARI, List.of(
                "Esas Faaliyet Kârı", "Esas Faaliyet Karı", "EBIT", "Operating Income"
        ));
        ALIASES.put(FinancialItemKey.FAVOK, List.of(
                "FAVÖK", "FAVOK", "EBITDA", "Faiz Amortisman Vergi Öncesi Kar"
        ));
        ALIASES.put(FinancialItemKey.NET_DONEM_KARI, List.of(
                "Net Dönem Karı", "Net Dönem Kârı",
                "Ana Ortaklık Net Dönem Karı", "Ana Ortaklık Net Dönem Kârı",
                "Dönem Karı/Zararı", "Net Profit"
        ));
        ALIASES.put(FinancialItemKey.OZKAYNAKLAR, List.of(
                "Özkaynaklar", "Ana Ortaklığa Ait Özkaynaklar", "Equity"
        ));
        ALIASES.put(FinancialItemKey.TOPLAM_VARLIKLAR, List.of(
                "Toplam Varlıklar", "Toplam Aktifler", "Total Assets"
        ));
        ALIASES.put(FinancialItemKey.TOPLAM_YUKUMLULUKLER, List.of(
                "Toplam Yükümlülükler", "Toplam Borçlar", "Total Liabilities"
        ));
        ALIASES.put(FinancialItemKey.ODENMIS_SERMAYE, List.of(
                "Ödenmiş Sermaye", "Çıkarılmış Sermaye", "Paid-in Capital"
        ));
    }

    // Pre-built index: normalized alias → FinancialItemKey
    private final Map<String, FinancialItemKey> normalizedIndex;

    public FinancialItemDictionary() {
        normalizedIndex = new HashMap<>();
        for (Map.Entry<FinancialItemKey, List<String>> entry : ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                normalizedIndex.put(normalize(alias), entry.getKey());
            }
        }
    }

    public Optional<FinancialItemKey> match(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(normalizedIndex.get(normalize(rawLabel)));
    }

    // Also expose aliases for reporting/debug purposes
    public static Map<FinancialItemKey, List<String>> getAliases() {
        return Collections.unmodifiableMap(ALIASES);
    }

    String normalize(String input) {
        if (input == null) return "";
        return input
                .replace('İ', 'i')          // dotted capital I → i before toLowerCase
                .toLowerCase(Locale.ROOT)
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ı', 'i')
                .replace('ç', 'c')
                .replaceAll("[^a-z0-9 ]", " ")   // strip punctuation
                .replaceAll("\\s+", " ")
                .trim();
    }
}
