package com.emrehalli.financeportal.technicalanalysis.fundamental.scheduler;

import com.emrehalli.financeportal.technicalanalysis.drawing.repository.ChartDrawingRepository;
import com.emrehalli.financeportal.technicalanalysis.drawing.service.PortfolioAlertIntegrationService;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.service.FundamentalAnalysisService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundamentalRatiosSchedulerTest {

    @Test
    void recalculateFundamentalRatios_should_call_calculateRatios_once_per_distinct_instrument() {
        FundamentalAnalysisService fundamentalAnalysisService = mock(FundamentalAnalysisService.class);
        CompanyFinancialsRepository companyFinancialsRepository = mock(CompanyFinancialsRepository.class);

        // DB DISTINCT zaten benzersiz ID döner; duplicate financial kayıtları olsa bile her ID bir kez gelir.
        when(companyFinancialsRepository.findDistinctInstrumentIds()).thenReturn(List.of(1L, 2L));

        FundamentalRatiosScheduler scheduler = new FundamentalRatiosScheduler(
                fundamentalAnalysisService,
                companyFinancialsRepository,
                mock(ChartDrawingRepository.class),
                mock(PortfolioAlertIntegrationService.class));

        scheduler.recalculateFundamentalRatios();

        verify(fundamentalAnalysisService, times(1)).calculateRatios(eq(1L), isNull());
        verify(fundamentalAnalysisService, times(1)).calculateRatios(eq(2L), isNull());
        verify(companyFinancialsRepository, never()).findAll();
    }

    @Test
    void recalculateFundamentalRatios_should_continue_when_one_instrument_fails() {
        FundamentalAnalysisService fundamentalAnalysisService = mock(FundamentalAnalysisService.class);
        CompanyFinancialsRepository companyFinancialsRepository = mock(CompanyFinancialsRepository.class);

        when(companyFinancialsRepository.findDistinctInstrumentIds()).thenReturn(List.of(1L, 2L));
        when(fundamentalAnalysisService.calculateRatios(eq(1L), isNull()))
                .thenThrow(new RuntimeException("boom"));

        FundamentalRatiosScheduler scheduler = new FundamentalRatiosScheduler(
                fundamentalAnalysisService,
                companyFinancialsRepository,
                mock(ChartDrawingRepository.class),
                mock(PortfolioAlertIntegrationService.class));

        scheduler.recalculateFundamentalRatios();

        // 1L hesaplaması patlasa bile per-instrument try/catch sayesinde 2L işlenmeye devam etmeli.
        verify(fundamentalAnalysisService).calculateRatios(eq(2L), isNull());
    }
}
