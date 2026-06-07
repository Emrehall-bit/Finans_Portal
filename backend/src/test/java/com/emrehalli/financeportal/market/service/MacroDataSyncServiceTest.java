package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MacroIndicator;
import com.emrehalli.financeportal.market.domain.enums.MacroFrequency;
import com.emrehalli.financeportal.market.domain.enums.MacroSourceName;
import com.emrehalli.financeportal.market.domain.enums.MacroUnit;
import com.emrehalli.financeportal.market.domain.enums.MacroValueType;
import com.emrehalli.financeportal.market.persistence.MacroIndicatorRepository;
import com.emrehalli.financeportal.market.persistence.MacroObservationRepository;
import com.emrehalli.financeportal.market.provider.macro.tcmb.TcmbMacroProvider;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroObservationParseResult;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroSeriesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MacroDataSyncServiceTest {

    private TcmbMacroProvider tcmbMacroProvider;
    private MacroIndicatorRepository indicatorRepository;
    private MacroObservationRepository observationRepository;
    private MacroDataSyncService service;

    private final MacroIndicator cpiIndicator = indicator(1L, "CPI_TR", "TÃœFE");
    private final MacroIndicator ppiIndicator = indicator(2L, "PPI_TR", "YÄ°-ÃœFE");
    private final MacroIndicator policyRateIndicator = indicator(3L, "POLICY_RATE_TR", "TCMB Politika Faizi");
    private final MacroIndicator unemploymentIndicator = indicator(4L, "UNEMPLOYMENT_TR", "Ä°ÅŸsizlik OranÄ±");
    private final MacroIndicator laborForceIndicator = indicator(5L, "LABOR_FORCE_PARTICIPATION_TR", "Ä°ÅŸgÃ¼cÃ¼ne KatÄ±lÄ±m OranÄ±");

    @BeforeEach
    void setUp() {
        tcmbMacroProvider = mock(TcmbMacroProvider.class);
        indicatorRepository = mock(MacroIndicatorRepository.class);
        observationRepository = mock(MacroObservationRepository.class);
        service = new MacroDataSyncService(tcmbMacroProvider, indicatorRepository, observationRepository);
    }

    @Test
    void syncCpiFromTcmb_savesNewObservations() {
        when(indicatorRepository.findByCode("CPI_TR")).thenReturn(Optional.of(cpiIndicator));
        when(tcmbMacroProvider.fetchSeries(any(MacroSeriesRequest.class)))
                .thenReturn(cpiObservations("2026-04", LocalDate.of(2026, 4, 1)));
        when(observationRepository.existsByIndicatorAndObservationDateAndValueType(any(), any(), any())).thenReturn(false);
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MacroSyncResult result = service.syncCpiFromTcmb("01-04-2026", "01-04-2026");

        assertThat(result.indicatorCodes()).containsExactly("CPI_TR");
        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.duplicates()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
        verify(observationRepository, times(2)).save(any());
    }

    @Test
    void syncCpiFromTcmb_skipsDuplicates() {
        when(indicatorRepository.findByCode("CPI_TR")).thenReturn(Optional.of(cpiIndicator));
        when(tcmbMacroProvider.fetchSeries(any(MacroSeriesRequest.class)))
                .thenReturn(cpiObservations("2026-04", LocalDate.of(2026, 4, 1)));
        when(observationRepository.existsByIndicatorAndObservationDateAndValueType(any(), any(), any())).thenReturn(true);

        MacroSyncResult result = service.syncCpiFromTcmb("01-04-2026", "01-04-2026");

        assertThat(result.saved()).isEqualTo(0);
        assertThat(result.duplicates()).isEqualTo(2);
        verify(observationRepository, never()).save(any());
    }

    @Test
    void syncPpiFromTcmb_savesNewObservations() {
        when(indicatorRepository.findByCode("PPI_TR")).thenReturn(Optional.of(ppiIndicator));
        when(tcmbMacroProvider.fetchSeries(any(MacroSeriesRequest.class)))
                .thenReturn(List.of(
                        new MacroObservationParseResult("PPI_TR", "2026-04", LocalDate.of(2026, 4, 1), new BigDecimal("3.17"), MacroValueType.MONTHLY_CHANGE),
                        new MacroObservationParseResult("PPI_TR", "2026-04", LocalDate.of(2026, 4, 1), new BigDecimal("28.59"), MacroValueType.YEARLY_CHANGE)
                ));
        when(observationRepository.existsByIndicatorAndObservationDateAndValueType(any(), any(), any())).thenReturn(false);
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MacroSyncResult result = service.syncPpiFromTcmb("01-04-2026", "01-04-2026");

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(2);
    }

    @Test
    void syncPolicyRateFromTcmb_savesNewObservation() {
        when(indicatorRepository.findByCode("POLICY_RATE_TR")).thenReturn(Optional.of(policyRateIndicator));
        when(tcmbMacroProvider.fetchSeries(any(MacroSeriesRequest.class))).thenReturn(List.of(
                new MacroObservationParseResult("POLICY_RATE_TR", "2026-03", LocalDate.of(2026, 3, 1),
                        new BigDecimal("37.00"), MacroValueType.POLICY_RATE)
        ));
        when(observationRepository.existsByIndicatorAndObservationDateAndValueType(any(), any(), any())).thenReturn(false);
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MacroSyncResult result = service.syncPolicyRateFromTcmb("01-09-2013", "01-03-2026");

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.saved()).isEqualTo(1);
    }

    @Test
    void syncLaborMarketFromTcmb_savesBothIndicatorsFromOneRequest() {
        when(indicatorRepository.findByCode("UNEMPLOYMENT_TR")).thenReturn(Optional.of(unemploymentIndicator));
        when(indicatorRepository.findByCode("LABOR_FORCE_PARTICIPATION_TR")).thenReturn(Optional.of(laborForceIndicator));
        when(tcmbMacroProvider.fetchSeries(any(MacroSeriesRequest.class))).thenReturn(List.of(
                new MacroObservationParseResult("UNEMPLOYMENT_TR",              "2026-03", LocalDate.of(2026, 3, 1), new BigDecimal("8.10"),  MacroValueType.UNEMPLOYMENT_RATE),
                new MacroObservationParseResult("LABOR_FORCE_PARTICIPATION_TR", "2026-03", LocalDate.of(2026, 3, 1), new BigDecimal("51.80"), MacroValueType.LABOR_FORCE_PARTICIPATION_RATE)
        ));
        when(observationRepository.existsByIndicatorAndObservationDateAndValueType(any(), any(), any())).thenReturn(false);
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MacroSyncResult result = service.syncLaborMarketFromTcmb("01-09-2013", "01-03-2026");

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);
        verify(observationRepository, times(2)).save(any());
    }

    @Test
    void syncSeries_createsIndicatorIfMissing() {
        when(indicatorRepository.findByCode("CPI_TR")).thenReturn(Optional.empty());
        when(indicatorRepository.save(any())).thenReturn(cpiIndicator);
        when(tcmbMacroProvider.fetchSeries(any())).thenReturn(List.of());

        service.syncCpiFromTcmb("01-04-2026", "01-04-2026");

        verify(indicatorRepository).save(any(MacroIndicator.class));
    }

    @Test
    void syncSeries_returnsZeroStatsOnEmptyFetch() {
        when(indicatorRepository.findByCode(eq("CPI_TR"))).thenReturn(Optional.of(cpiIndicator));
        when(tcmbMacroProvider.fetchSeries(any())).thenReturn(List.of());

        MacroSyncResult result = service.syncCpiFromTcmb("01-04-2026", "01-04-2026");

        assertThat(result.fetched()).isEqualTo(0);
        assertThat(result.saved()).isEqualTo(0);
        assertThat(result.duplicates()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
    }

    private List<MacroObservationParseResult> cpiObservations(String period, LocalDate date) {
        return List.of(
                new MacroObservationParseResult("CPI_TR", period, date, new BigDecimal("4.18"), MacroValueType.MONTHLY_CHANGE),
                new MacroObservationParseResult("CPI_TR", period, date, new BigDecimal("32.37"), MacroValueType.YEARLY_CHANGE)
        );
    }

    private static MacroIndicator indicator(Long id, String code, String name) {
        return MacroIndicator.builder()
                .id(id).code(code).name(name)
                .source(MacroSourceName.TCMB_EVDS).frequency(MacroFrequency.MONTHLY)
                .unit(MacroUnit.PERCENT).active(true).build();
    }

}




