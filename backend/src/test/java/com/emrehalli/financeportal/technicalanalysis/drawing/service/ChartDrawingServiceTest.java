package com.emrehalli.financeportal.technicalanalysis.drawing.service;

import com.emrehalli.financeportal.technicalanalysis.drawing.dto.DrawingDtos.Request;
import com.emrehalli.financeportal.technicalanalysis.drawing.entity.ChartDrawing;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException.DrawingNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException.PremiumRequiredException;
import com.emrehalli.financeportal.technicalanalysis.drawing.repository.ChartDrawingRepository;
import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.repository.AlertRepository;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChartDrawingServiceTest {

    @Mock ChartDrawingRepository chartDrawingRepository;
    @Mock MarketInstrumentRepository marketInstrumentRepository;
    @Mock UserRepository userRepository;
    @Mock AlertRepository alertRepository;
    @Mock PortfolioAlertIntegrationService portfolioAlertIntegrationService;

    @InjectMocks ChartDrawingService service;

    private User user;
    private MarketInstrument instrument;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).keycloakId("kc-123").email("test@test.com").role(UserRole.USER).build();
        instrument = MarketInstrument.builder().id(1L).instrumentCode("THYAO").build();
    }

    @Test
    void premium_olmayan_kullanici_fibonacci_cizmeye_calisirsa_exception_firlatmali() {
        Request req = new Request();
        req.setDrawingType("FIBONACCI_RETRACEMENT");
        req.setTimeframe("1d");
        req.setPoints(List.of(Map.of("time", 1700000000L, "price", 294.50)));

        assertThatThrownBy(() ->
                service.saveDrawing("kc-123", UserRole.USER, "THYAO", req))
                .isInstanceOf(PremiumRequiredException.class)
                .hasMessageContaining("Premium");
    }

    @Test
    void premium_kullanici_fibonacci_cizebilmeli() {
        Request req = new Request();
        req.setDrawingType("FIBONACCI_RETRACEMENT");
        req.setTimeframe("1d");
        req.setPoints(List.of(Map.of("time", 1700000000L, "price", 294.50)));

        when(userRepository.findByKeycloakId("kc-123")).thenReturn(Optional.of(user));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("THYAO")).thenReturn(Optional.of(instrument));
        when(chartDrawingRepository.save(any())).thenAnswer(inv -> {
            ChartDrawing d = inv.getArgument(0);
            return ChartDrawing.builder()
                    .id(99L).user(user).instrument(instrument)
                    .timeframe(d.getTimeframe()).drawingType(d.getDrawingType())
                    .points(d.getPoints()).style(d.getStyle())
                    .isPremiumFeature(true).build();
        });

        var result = service.saveDrawing("kc-123", UserRole.USER_PREMIUM, "THYAO", req);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.isPremiumFeature()).isTrue();
    }

    @Test
    void baskasinin_cizimini_silmeye_calisirsak_exception_firlatmali() {
        when(userRepository.findByKeycloakId("kc-123")).thenReturn(Optional.of(user));
        when(chartDrawingRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDrawing("kc-123", 99L))
                .isInstanceOf(DrawingNotFoundException.class);
    }

    @Test
    void drawing_alert_link_sonrasi_threshold_guncellenmeli() {
        ChartDrawing drawing = ChartDrawing.builder()
                .id(1L).user(user).instrument(instrument)
                .timeframe("1d").drawingType("STOPLOSS_LINE")
                .points(List.of(Map.of("time", 1700000000L, "price", 280.0)))
                .isAlertLinked(false)
                .build();

        Alert alert = Alert.builder()
                .id(5L).user(user)
                .instrumentCode("THYAO")
                .targetPrice(new BigDecimal("300.00"))
                .build();

        when(userRepository.findByKeycloakId("kc-123")).thenReturn(Optional.of(user));
        when(chartDrawingRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(drawing));
        when(alertRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(alert));
        when(chartDrawingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.linkDrawingToAlert("kc-123", 1L, 5L);

        // syncDrawingWithAlert Ã§aÄŸrÄ±lmÄ±ÅŸ olmalÄ±
        verify(portfolioAlertIntegrationService, times(1)).syncDrawingWithAlert(any());
        // Drawing alert baÄŸlÄ± olarak kaydedilmeli
        verify(chartDrawingRepository, times(1)).save(argThat(d -> d.isAlertLinked() && d.getLinkedAlertId() == 5L));
    }
}

