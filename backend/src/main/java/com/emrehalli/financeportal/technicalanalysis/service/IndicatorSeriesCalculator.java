package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link IndicatorType} -> (SMA | RSI) yönlendirmesini tek noktada tutan, state taşımayan
 * hesap orkestratörü. Saf hesaplama mantığı {@link MovingAverageService} ve {@link RsiService}
 * içinde kalır; bu sınıf yalnızca hangi göstergenin hangi hesaplayıcıya ve hangi periyotla
 * gideceğini bilir. Böylece period bilgisi servislerde tekrar etmez.
 */
@Component
public class IndicatorSeriesCalculator {

    private final MovingAverageService movingAverageService;
    private final RsiService rsiService;

    public IndicatorSeriesCalculator(MovingAverageService movingAverageService, RsiService rsiService) {
        this.movingAverageService = movingAverageService;
        this.rsiService = rsiService;
    }

    /**
     * Verilen kapanışlar üzerinde <b>yalnızca istenen</b> göstergeleri hesaplar. Dönen map
     * sadece istenen tipleri içerir; istenmeyen gösterge için seri hiç üretilmez (gereksiz
     * hesaplama yapılmaz). İstenmeyen göstergeye ait değer, çağıran tarafta {@code null} kalır.
     */
    public Map<IndicatorType, List<BigDecimal>> calculate(List<BigDecimal> closes, Set<IndicatorType> indicators) {
        Map<IndicatorType, List<BigDecimal>> series = new EnumMap<>(IndicatorType.class);
        for (IndicatorType indicator : indicators) {
            series.put(indicator, calculateSeries(closes, indicator));
        }
        return series;
    }

    private List<BigDecimal> calculateSeries(List<BigDecimal> closes, IndicatorType indicator) {
        return switch (indicator) {
            case SMA7, SMA20, SMA50 -> movingAverageService.calculateSimpleMovingAverage(closes, indicator.period());
            case RSI14 -> rsiService.calculateRsi(closes, indicator.period());
        };
    }
}
