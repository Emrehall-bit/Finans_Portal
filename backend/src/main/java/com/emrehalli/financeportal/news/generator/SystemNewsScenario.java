package com.emrehalli.financeportal.news.generator;

import java.math.BigDecimal;

/**
 * Fiyat hareketini yön ve şiddete göre sınıflandırarak sistem üretimi haberler için uygun ton ve
 * şablon ailesini seçer. Büyüklüğü yapılandırılmış eşik değerinin iki katına ulaşan hareketler
 * "aşırı" olarak değerlendirilir; aksi halde "güçlü"/"sert" hareket olarak işlenir.
 */
public enum SystemNewsScenario {

    STRONG_RISE,
    EXTREME_RISE,
    SHARP_FALL,
    EXTREME_FALL;

    /**
     * İşaretli yüzde değişim ve temel önem eşiğinden senaryoyu türetir.
     *
     * @param changePercent enstrümanın işaretli yüzde değişimi
     * @param threshold     anlamlı kabul edilen temel büyüklük; bu değerin iki katına ulaşılması
     *                      senaryoyu aşırı varyantına yükseltir
     * @return değişimin işareti ve büyüklüğüne göre eşleşen senaryo
     */
    public static SystemNewsScenario from(BigDecimal changePercent, BigDecimal threshold) {
        boolean positive = changePercent.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal abs = changePercent.abs();
        BigDecimal doubleThreshold = threshold.multiply(BigDecimal.valueOf(2));
        if (abs.compareTo(doubleThreshold) >= 0) {
            return positive ? EXTREME_RISE : EXTREME_FALL;
        }
        return positive ? STRONG_RISE : SHARP_FALL;
    }

    /**
     * @return bu senaryo yukarı yönlü (pozitif) bir fiyat hareketini temsil ediyorsa {@code true}
     */
    public boolean isRise() {
        return this == STRONG_RISE || this == EXTREME_RISE;
    }
}
