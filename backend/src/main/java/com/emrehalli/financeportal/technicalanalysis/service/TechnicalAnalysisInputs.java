package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Teknik analiz servislerinin paylaştığı, state taşımayan input yardımcıları. İki ayrı sorumluluk
 * barındırır ve bunlar bilinçli olarak ayrı metotlardadır:
 * <ul>
 *   <li><b>Input validation</b> ({@link #validateSymbol(String)}) — sembol doğrulaması.</li>
 *   <li><b>Cache-key normalization</b> ({@link #normalizeToken(String)} ve {@code *CacheKey} metotları) —
 *       canonical cache anahtarı üretimi.</li>
 * </ul>
 * Daha önce {@code TechnicalAnalysisService} ve {@code TechnicalCandleService} içinde birebir
 * tekrarlanan normalize/validation mantığı burada tek noktada toplanmıştır.
 */
public final class TechnicalAnalysisInputs {

    private static final int MAX_SYMBOL_LENGTH = 30;
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-:]+$");

    private TechnicalAnalysisInputs() {
    }

    // --- Input validation ---

    /**
     * Sembolü doğrular: boş/null değilse, en fazla {@value #MAX_SYMBOL_LENGTH} karakter ise ve
     * yalnızca harf/rakam/{@code . - _ :} içeriyorsa geçerlidir. Verilen değeri <b>trim etmez</b>;
     * çağıran taraf trim davranışını kendisi yönetir (örn. candle servisi kendi geçmiş davranışını
     * koruyabilmek için trim edilmiş sembolü geçirir). Hata mesajları ve HTTP status davranışı,
     * önceki servis-içi doğrulamalarla birebir aynıdır.
     */
    static void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new TechnicalAnalysisException.Validation("symbol cannot be blank");
        }
        if (symbol.length() > MAX_SYMBOL_LENGTH) {
            throw new TechnicalAnalysisException.Validation(
                    "symbol too long: maximum " + MAX_SYMBOL_LENGTH + " characters allowed");
        }
        if (!SYMBOL_PATTERN.matcher(symbol).matches()) {
            throw new TechnicalAnalysisException.Validation(
                    "symbol contains invalid characters: only letters, digits, '.', '-', '_', ':' are allowed");
        }
    }

    // --- Cache-key normalization ---

    /** Trim + upper-case (ROOT) normalizasyonu; cache key parçaları için kullanılır. */
    static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String comparisonCacheKey(String symbols, LocalDate from, LocalDate to) {
        return canonicalCsv(symbols, "") + ':' + from + ':' + to;
    }

    public static String candleCacheKey(String symbol, String range, String interval) {
        return normalizeToken(symbol) + ':'
                + normalizeToken(isBlank(range) ? "6m" : range) + ':'
                + normalizeToken(isBlank(interval) ? "1d" : interval);
    }

    /**
     * Virgülle ayrılmış bir listeyi sırası ve tekrarı bağımsız (canonical) hale getirir;
     * böylece "SMA20,SMA7" ile "sma7, sma20" aynı cache anahtarına düşer.
     */
    private static String canonicalCsv(String csv, String fallback) {
        if (isBlank(csv)) {
            return fallback;
        }
        return Arrays.stream(csv.split(","))
                .map(TechnicalAnalysisInputs::normalizeToken)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "," + right)
                .orElse(fallback);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
