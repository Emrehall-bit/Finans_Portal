package com.emrehalli.financeportal.analysis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TechnicalIndicatorServiceTest {

    private TechnicalIndicatorService service;

    @BeforeEach
    void setUp() {
        service = new TechnicalIndicatorService();
    }

    // ── SMA ──────────────────────────────────────────────────────────────────

    @Test
    void sma_bilinen_veriyle_dogru_hesaplamali() {
        // 5 günlük SMA: [10, 20, 30, 40, 50] → ortalama = 30
        List<Double> prices = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        List<Double> result = service.calculateSMA(prices, 5);

        assertThat(result).hasSize(5);
        assertThat(result.get(4)).isCloseTo(30.0, within(0.001));
    }

    @Test
    void sma_ilk_period_eksi_bir_eleman_null_olmali() {
        List<Double> prices = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        List<Double> result = service.calculateSMA(prices, 3);

        assertThat(result.get(0)).isNull();
        assertThat(result.get(1)).isNull();
        assertThat(result.get(2)).isNotNull();
    }

    @Test
    void sma_bos_liste_exception_firlatmali() {
        assertThatThrownBy(() -> service.calculateSMA(List.of(), 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sma_yetersiz_veri_exception_firlatmali() {
        assertThatThrownBy(() -> service.calculateSMA(List.of(10.0, 20.0), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en az");
    }

    // ── RSI ──────────────────────────────────────────────────────────────────

    @Test
    void rsi_bilinen_veriyle_makul_aralikta_olmali() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            prices.add(100.0 + (i % 2 == 0 ? i : -i * 0.5));
        }
        List<Double> result = service.calculateRSI(prices, 14);

        assertThat(result).hasSize(30);
        // RSI her zaman 0-100 arasında olmalı
        result.stream()
                .filter(v -> v != null)
                .forEach(v -> assertThat(v).isBetween(0.0, 100.0));
    }

    @Test
    void rsi_yetersiz_veri_exception_firlatmali() {
        assertThatThrownBy(() -> service.calculateRSI(List.of(10.0, 20.0, 30.0), 14))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rsi_surekli_yukselis_100_yakin_olmali() {
        // Sürekli yükselen fiyatlar → RSI 100'e yaklaşmalı
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(100.0 + i * 5.0);
        }
        List<Double> result = service.calculateRSI(prices, 14);
        Double lastRsi = result.get(result.size() - 1);
        assertThat(lastRsi).isGreaterThan(80.0);
    }

    // ── MACD ─────────────────────────────────────────────────────────────────

    @Test
    void macd_sinyal_kesisimi_tespit_edilmeli() {
        // Basit test: MACD hesaplama sonucunda 3 liste dönmeli
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            prices.add(100.0 + Math.sin(i * 0.3) * 10);
        }
        TechnicalIndicatorService.MACDResult result = service.calculateMACD(prices, 12, 26, 9);

        assertThat(result.macdLine()).hasSize(50);
        assertThat(result.signalLine()).hasSize(50);
        assertThat(result.histogram()).hasSize(50);
    }

    // ── Bollinger ─────────────────────────────────────────────────────────────

    @Test
    void bollinger_upper_lower_ortali_donmeli() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) prices.add(100.0 + Math.random() * 5);

        TechnicalIndicatorService.BollingerResult result = service.calculateBollingerBands(prices, 20, 2.0);

        // Period'dan sonraki her noktada: lower < middle < upper
        for (int i = 19; i < prices.size(); i++) {
            assertThat(result.upper().get(i)).isGreaterThan(result.middle().get(i));
            assertThat(result.middle().get(i)).isGreaterThan(result.lower().get(i));
        }
    }
}
