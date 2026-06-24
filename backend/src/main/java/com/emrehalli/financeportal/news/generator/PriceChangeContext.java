package com.emrehalli.financeportal.news.generator;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;

import java.math.BigDecimal;

/**
 * Sistem tarafından üretilecek bir haber öğesi için dikkate değer fiyat hareketini tanımlayan
 * değişmez girdi paketi. Enstrüman kimliğini, insan tarafından okunabilir görüntüleme adını,
 * tipini, işaretli yüzde değişimi ve sınıflandırılmış {@link SystemNewsScenario} değerini taşır.
 *
 * @param symbol         enstrümanın işlem sembolü
 * @param displayName    üretilen metinde gösterilen, çözümlenmiş okunabilir ad
 * @param instrumentType enstrüman kategorisi (kripto, hisse, FX, emtia, fon)
 * @param changePercent  haberi tetikleyen işaretli yüzde değişim
 * @param scenario       değişimden türetilen şiddet/yön senaryosu
 */
public record PriceChangeContext(
        String symbol,
        String displayName,
        InstrumentType instrumentType,
        BigDecimal changePercent,
        SystemNewsScenario scenario
) {
    /**
     * @return iki ondalığa yuvarlanmış mutlak yüzde değişim; üretilen başlık ve özetlere
     *         eklenmek üzere biçimlendirilmiş hali
     */
    public String formattedChange() {
        return changePercent.abs().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
