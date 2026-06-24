package com.emrehalli.financeportal.market.service.mock;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MockDerivativesSeedServiceTest {

    private MarketInstrumentRepository instrumentRepository;
    private MarketPriceRepository priceRepository;
    private JdbcTemplate jdbc;
    private MockDerivativesSeedService service;

    private static final int EXPECTED_FUTURES = 23;
    private static final int EXPECTED_BONDS   = 22;

    @BeforeEach
    void setUp() {
        instrumentRepository = mock(MarketInstrumentRepository.class);
        priceRepository      = mock(MarketPriceRepository.class);
        jdbc                 = mock(JdbcTemplate.class);
        service              = new MockDerivativesSeedService(instrumentRepository, priceRepository, jdbc);
    }

    // -----------------------------------------------------------------------
    // Instrument count assertions
    // -----------------------------------------------------------------------

    @Test
    void seed_returns_correct_futures_count() {
        stubNewInstrument(InstrumentType.FUTURES, SourceName.BIST);
        stubNewInstrument(InstrumentType.BOND, SourceName.TCMB);
        stubBatchUpdate();

        MockSeedResult result = service.seed();

        assertThat(result.futuresInstruments()).isEqualTo(EXPECTED_FUTURES);
    }

    @Test
    void seed_returns_correct_bond_count() {
        stubNewInstrument(InstrumentType.FUTURES, SourceName.BIST);
        stubNewInstrument(InstrumentType.BOND, SourceName.TCMB);
        stubBatchUpdate();

        MockSeedResult result = service.seed();

        assertThat(result.bondInstruments()).isEqualTo(EXPECTED_BONDS);
    }

    @Test
    void seed_inserts_one_price_per_instrument() {
        stubNewInstrument(InstrumentType.FUTURES, SourceName.BIST);
        stubNewInstrument(InstrumentType.BOND, SourceName.TCMB);
        stubBatchUpdate();

        MockSeedResult result = service.seed();

        assertThat(result.priceRecords()).isEqualTo(EXPECTED_FUTURES + EXPECTED_BONDS);
    }

    // -----------------------------------------------------------------------
    // Idempotency: no duplicate instruments on second call
    // -----------------------------------------------------------------------

    @Test
    void seed_does_not_create_duplicate_instruments_when_already_exist() {
        when(instrumentRepository.findByInstrumentCodeAndSourceName(anyString(), eq(SourceName.BIST)))
                .thenReturn(Optional.of(existingInstrument(1L, "FUT_XU030_202606", InstrumentType.FUTURES)));
        when(instrumentRepository.findByInstrumentCodeAndSourceName(anyString(), eq(SourceName.TCMB)))
                .thenReturn(Optional.of(existingInstrument(2L, "TR2Y", InstrumentType.BOND)));
        when(instrumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchUpdate();
        when(priceRepository.save(any())).thenReturn(mock(MarketPrice.class));

        service.seed();

        verify(instrumentRepository, never()).save(argThat(mi ->
                mi instanceof MarketInstrument instrument && instrument.getId() == null));
    }

    // -----------------------------------------------------------------------
    // History batch insert is invoked for each instrument
    // -----------------------------------------------------------------------

    @Test
    void seed_calls_batch_insert_for_every_instrument() {
        stubNewInstrument(InstrumentType.FUTURES, SourceName.BIST);
        stubNewInstrument(InstrumentType.BOND, SourceName.TCMB);
        stubBatchUpdate();

        service.seed();

        verify(jdbc, atLeast(EXPECTED_FUTURES + EXPECTED_BONDS)).batchUpdate(anyString(), any(List.class));
    }

    // -----------------------------------------------------------------------
    // Bond changeRate must be non-null (Issue #3 fix validation)
    // -----------------------------------------------------------------------

    @Test
    void seed_bond_prices_have_non_null_change_rate() {
        stubNewInstrument(InstrumentType.FUTURES, SourceName.BIST);
        stubNewInstrument(InstrumentType.BOND, SourceName.TCMB);
        stubBatchUpdate();

        ArgumentCaptor<MarketPrice> priceCaptor = ArgumentCaptor.forClass(MarketPrice.class);
        service.seed();
        verify(priceRepository, atLeast(1)).save(priceCaptor.capture());

        List<MarketPrice> bondPrices = priceCaptor.getAllValues().stream()
                .filter(p -> p.getSourceName() == SourceName.TCMB)
                .toList();
        assertThat(bondPrices).isNotEmpty();
        bondPrices.forEach(p ->
                assertThat(p.getChangeRate())
                        .as("Bond %s must have non-null changeRate", p.getSourceName())
                        .isNotNull());
    }

    // -----------------------------------------------------------------------
    // BIST-30 index futures must stay within realistic price band
    // -----------------------------------------------------------------------

    @Test
    void futuresXu030_last_price_within_expected_band() {
        MockDerivativesSeedService.PriceSeries series = service.generateSeries("FUT_XU030_202606", 12500.0, 0.010);
        BigDecimal last = series.lastClose();

        assertThat(last).isGreaterThan(BigDecimal.valueOf(11_000.0));
        assertThat(last).isLessThan(BigDecimal.valueOf(15_500.0));
    }

    // -----------------------------------------------------------------------
    // Deterministic price series
    // -----------------------------------------------------------------------

    @Test
    void generateSeries_is_deterministic_for_same_code() {
        MockDerivativesSeedService.PriceSeries s1 = service.generateSeries("FUT_USDTRY_202606", 38.5, 0.008);
        MockDerivativesSeedService.PriceSeries s2 = service.generateSeries("FUT_USDTRY_202606", 38.5, 0.008);

        assertThat(s1.lastClose()).isEqualByComparingTo(s2.lastClose());
        assertThat(s1.bars()).hasSameSizeAs(s2.bars());
    }

    @Test
    void generateSeries_produces_different_series_for_different_codes() {
        MockDerivativesSeedService.PriceSeries s1 = service.generateSeries("FUT_USDTRY_202606", 38.5, 0.008);
        MockDerivativesSeedService.PriceSeries s2 = service.generateSeries("FUT_EURTRY_202606", 38.5, 0.008);

        assertThat(s1.lastClose()).isNotEqualByComparingTo(s2.lastClose());
    }

    @Test
    void generateSeries_last_close_stays_within_mean_reversion_bounds() {
        // With ±18% bounds, TR10Y (base=33.5) must stay in [27.5, 40.0]
        MockDerivativesSeedService.PriceSeries series = service.generateSeries("TR10Y", 33.5, 0.004);
        BigDecimal last = series.lastClose();
        assertThat(last).isGreaterThan(BigDecimal.valueOf(27.0));
        assertThat(last).isLessThan(BigDecimal.valueOf(40.0));
    }

    @Test
    void generateSeries_covers_approximately_three_business_years() {
        MockDerivativesSeedService.PriceSeries series = service.generateSeries("TR2Y", 38.5, 0.005);
        // 3 years of business days ≈ 756, allow ±30 for weekends on boundary
        assertThat(series.bars()).hasSizeGreaterThan(700);
        assertThat(series.bars()).hasSizeLessThan(800);
    }

    // -----------------------------------------------------------------------
    // Last price matches history series end
    // -----------------------------------------------------------------------

    @Test
    void priceSeries_lastClose_matches_last_bar_close() {
        MockDerivativesSeedService.PriceSeries series = service.generateSeries("FUT_XU030_202606", 12500.0, 0.010);

        BigDecimal lastBarClose  = series.bars().get(series.bars().size() - 1).close();
        BigDecimal reportedClose = series.lastClose();

        assertThat(reportedClose).isEqualByComparingTo(lastBarClose);
    }

    // -----------------------------------------------------------------------
    // Business-days helper
    // -----------------------------------------------------------------------

    @Test
    void businessDaysBetween_excludes_weekends() {
        LocalDate mon = LocalDate.of(2024, 1, 8);
        LocalDate fri = LocalDate.of(2024, 1, 12);
        List<LocalDate> days = MockDerivativesSeedService.businessDaysBetween(mon, fri);

        assertThat(days).hasSize(5);
        days.forEach(d -> assertThat(d.getDayOfWeek().getValue())
                .as("Day %s must not be a weekend", d)
                .isLessThanOrEqualTo(5));
    }

    @Test
    void businessDaysBetween_skips_saturday_and_sunday() {
        LocalDate fri = LocalDate.of(2024, 1, 12);
        LocalDate mon = LocalDate.of(2024, 1, 15);
        List<LocalDate> days = MockDerivativesSeedService.businessDaysBetween(fri, mon);

        assertThat(days).containsExactly(fri, mon);
    }

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    private void stubNewInstrument(InstrumentType type, SourceName source) {
        when(instrumentRepository.findByInstrumentCodeAndSourceName(anyString(), eq(source)))
                .thenReturn(Optional.empty());
        when(instrumentRepository.save(any(MarketInstrument.class))).thenAnswer(inv -> {
            MarketInstrument mi = inv.getArgument(0);
            return existingInstrument(1L,
                    mi.getInstrumentCode() != null ? mi.getInstrumentCode() : "X",
                    type);
        });
        when(priceRepository.save(any())).thenReturn(mock(MarketPrice.class));
    }

    private void stubBatchUpdate() {
        when(jdbc.batchUpdate(anyString(), any(List.class))).thenReturn(new int[]{1});
    }

    private static MarketInstrument existingInstrument(Long id, String code, InstrumentType type) {
        MarketInstrument mi = MarketInstrument.builder()
                .instrumentCode(code)
                .instrumentName(code)
                .instrumentType(type)
                .sourceName(type == InstrumentType.FUTURES ? SourceName.BIST : SourceName.TCMB)
                .build();
        mi.setId(id);
        return mi;
    }
}
