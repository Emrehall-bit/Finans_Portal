package com.emrehalli.financeportal.technicalanalysis.controller;

import com.emrehalli.financeportal.config.ObservabilityFilterConfig;
import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisExceptionHandler;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisValidationException;
import com.emrehalli.financeportal.technicalanalysis.mapper.TechnicalAnalysisMapper;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonPoint;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonResult;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonSeries;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisPoint;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TechnicalAnalysisController.class)
@Import({
        SecurityConfig.class,
        KeycloakJwtRoleConverter.class,
        TechnicalAnalysisMapper.class,
        TechnicalAnalysisExceptionHandler.class,
        ObservabilityFilterConfig.class
})
class TechnicalAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TechnicalAnalysisService technicalAnalysisService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @Test
    void analyzeReturnsRsi14InResponse() throws Exception {
        when(technicalAnalysisService.analyze(eq("USDTRY"), any(), any(), any()))
                .thenReturn(new TechnicalAnalysisResult(
                        "USDTRY",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 4, 27),
                        new BigDecimal("38.50"),
                        TrendDirection.UPTREND,
                        List.of(TechnicalSignal.PRICE_ABOVE_SMA20, TechnicalSignal.RSI_NEUTRAL),
                        Map.of(
                                IndicatorType.SMA7, new BigDecimal("38.20"),
                                IndicatorType.SMA20, new BigDecimal("37.90"),
                                IndicatorType.SMA50, new BigDecimal("36.80"),
                                IndicatorType.RSI14, new BigDecimal("54.12")
                        ),
                        List.of(new TechnicalAnalysisPoint(
                                LocalDate.of(2026, 4, 27),
                                new BigDecimal("38.50"),
                                new BigDecimal("38.20"),
                                new BigDecimal("37.90"),
                                new BigDecimal("36.80"),
                                new BigDecimal("54.12")
                        ))
                ));

        mockMvc.perform(get("/api/v1/technical-analysis/USDTRY")
                        .header("X-Request-Id", "ta-rsi-req")
                        .param("from", "2026-01-01")
                        .param("to", "2026-04-27")
                        .param("indicators", "SMA7,SMA20,SMA50,RSI14"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "ta-rsi-req"))
                .andExpect(jsonPath("$.symbol").value("USDTRY"))
                .andExpect(jsonPath("$.indicatorValues[?(@.indicator=='RSI14')].value").exists())
                .andExpect(jsonPath("$.points[0].rsi14").value(54.12));
    }

    @Test
    void analyzeReturnsBadRequestWhenFromIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/technical-analysis/USDTRY")
                        .param("to", "2026-04-27"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeReturnsBadRequestWhenToIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/technical-analysis/USDTRY")
                        .param("from", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeReturnsBadRequestWhenValidationFails() throws Exception {
        when(technicalAnalysisService.analyze(eq("USDTRY"), any(), any(), any()))
                .thenThrow(new TechnicalAnalysisValidationException("from cannot be after to"));

        mockMvc.perform(get("/api/v1/technical-analysis/USDTRY")
                        .param("from", "2026-04-27")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("from cannot be after to"));
    }

    @Test
    void analyzeReturnsNotFoundWhenHistoryIsMissing() throws Exception {
        when(technicalAnalysisService.analyze(eq("USDTRY"), any(), any(), any()))
                .thenThrow(new TechnicalAnalysisNotFoundException("Historical price data not found for symbol: USDTRY"));

        mockMvc.perform(get("/api/v1/technical-analysis/USDTRY")
                        .param("from", "2026-01-01")
                        .param("to", "2026-04-27"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Historical price data not found for symbol: USDTRY"));
    }

    @Test
    void compareEndpointStillReturnsNormalizedSeries() throws Exception {
        when(technicalAnalysisService.compare(eq("USDTRY,EURTRY"), any(), any()))
                .thenReturn(new ComparisonResult(
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 27),
                        List.of(
                                new ComparisonSeries("USDTRY", List.of(
                                        new ComparisonPoint(LocalDate.of(2026, 4, 1), new BigDecimal("38.00"), new BigDecimal("100.00")),
                                        new ComparisonPoint(LocalDate.of(2026, 4, 2), new BigDecimal("38.76"), new BigDecimal("102.00"))
                                )),
                                new ComparisonSeries("EURTRY", List.of(
                                        new ComparisonPoint(LocalDate.of(2026, 4, 1), new BigDecimal("41.00"), new BigDecimal("100.00")),
                                        new ComparisonPoint(LocalDate.of(2026, 4, 2), new BigDecimal("41.82"), new BigDecimal("102.00"))
                                ))
                        )
                ));

        mockMvc.perform(get("/api/v1/technical-analysis/compare")
                        .param("symbols", "USDTRY,EURTRY")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[0].symbol").value("USDTRY"))
                .andExpect(jsonPath("$.series[0].points[0].normalizedValue").value(100.00))
                .andExpect(jsonPath("$.series[1].symbol").value("EURTRY"));
    }

    @Test
    void compareReturnsBadRequestWhenSymbolsAreBlank() throws Exception {
        when(technicalAnalysisService.compare(eq(" "), any(), any()))
                .thenThrow(new TechnicalAnalysisValidationException("symbols parameter cannot be blank"));

        mockMvc.perform(get("/api/v1/technical-analysis/compare")
                        .param("symbols", " ")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-27"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("symbols parameter cannot be blank"));
    }
}
