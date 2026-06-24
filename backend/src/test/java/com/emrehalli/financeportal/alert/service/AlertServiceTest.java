package com.emrehalli.financeportal.alert.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.alert.dto.AlertResponseDto;
import com.emrehalli.financeportal.alert.dto.CreateAlertRequest;
import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.enums.AlertStatus;
import com.emrehalli.financeportal.alert.enums.ConditionType;
import com.emrehalli.financeportal.alert.repository.AlertRepository;
import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private AlertRepository alertRepository;
    private UserRepository userRepository;
    private MarketQueryService marketQueryService;
    private NotificationService notificationService;
    private CacheManager cacheManager;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        userRepository = mock(UserRepository.class);
        marketQueryService = mock(MarketQueryService.class);
        notificationService = mock(NotificationService.class);
        cacheManager = mock(CacheManager.class);
        alertService = new AlertService(alertRepository, userRepository, marketQueryService, notificationService, cacheManager);
    }

    @Test
    void createAlert_normalizes_symbol_to_uppercase() {
        Long userId = 1L;
        User user = user(userId);
        CreateAlertRequest request = request("thyao", ConditionType.ABOVE, new BigDecimal("100.00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.existsByUserIdAndInstrumentCodeIgnoreCaseAndConditionTypeAndTargetPriceAndStatus(
                any(), any(), any(), any(), any())).thenReturn(false);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertRepository.save(captor.capture())).thenAnswer(inv -> {
            Alert a = captor.getValue();
            a.setId(10L);
            return a;
        });

        AlertResponseDto response = alertService.createAlert(userId, request);

        assertThat(captor.getValue().getInstrumentCode()).isEqualTo("THYAO");
        assertThat(response.getInstrumentCode()).isEqualTo("THYAO");
    }

    @Test
    void createAlert_rejects_duplicate_active_alert_for_same_user_symbol_condition_and_target() {
        Long userId = 1L;
        CreateAlertRequest request = request("THYAO", ConditionType.ABOVE, new BigDecimal("100.00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(alertRepository.existsByUserIdAndInstrumentCodeIgnoreCaseAndConditionTypeAndTargetPriceAndStatus(
                userId, "THYAO", ConditionType.ABOVE, new BigDecimal("100.00"), AlertStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> alertService.createAlert(userId, request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void evaluateAlerts_above_condition_triggers_when_current_price_reaches_or_exceeds_target() {
        Long userId = 1L;
        Alert activeAlert = alert(1L, user(userId), "THYAO", ConditionType.ABOVE, new BigDecimal("100.00"), AlertStatus.ACTIVE);

        when(alertRepository.findByStatus(AlertStatus.ACTIVE)).thenReturn(List.of(activeAlert));
        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.of(snapshot("THYAO", new BigDecimal("110.00"))));
        when(alertRepository.findByUserIdAndStatusOrderByTriggeredAtDesc(userId, AlertStatus.TRIGGERED))
                .thenReturn(List.of());
        when(cacheManager.getCache(anyString())).thenReturn(null);

        alertService.evaluateActiveAlerts();

        assertThat(activeAlert.getStatus()).isEqualTo(AlertStatus.TRIGGERED);
        assertThat(activeAlert.getTriggeredAt()).isNotNull();
        verify(alertRepository).saveAll(anyList());
    }

    @Test
    void evaluateAlerts_above_condition_does_not_trigger_when_price_below_target() {
        Long userId = 1L;
        Alert activeAlert = alert(1L, user(userId), "THYAO", ConditionType.ABOVE, new BigDecimal("100.00"), AlertStatus.ACTIVE);

        when(alertRepository.findByStatus(AlertStatus.ACTIVE)).thenReturn(List.of(activeAlert));
        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.of(snapshot("THYAO", new BigDecimal("95.00"))));

        alertService.evaluateActiveAlerts();

        assertThat(activeAlert.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        verify(alertRepository, never()).saveAll(anyList());
    }

    @Test
    void evaluateAlerts_below_condition_triggers_when_current_price_reaches_or_drops_below_target() {
        Long userId = 1L;
        Alert activeAlert = alert(1L, user(userId), "THYAO", ConditionType.BELOW, new BigDecimal("90.00"), AlertStatus.ACTIVE);

        when(alertRepository.findByStatus(AlertStatus.ACTIVE)).thenReturn(List.of(activeAlert));
        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.of(snapshot("THYAO", new BigDecimal("85.00"))));
        when(alertRepository.findByUserIdAndStatusOrderByTriggeredAtDesc(userId, AlertStatus.TRIGGERED))
                .thenReturn(List.of());
        when(cacheManager.getCache(anyString())).thenReturn(null);

        alertService.evaluateActiveAlerts();

        assertThat(activeAlert.getStatus()).isEqualTo(AlertStatus.TRIGGERED);
        verify(alertRepository).saveAll(anyList());
    }

    @Test
    void evaluateAlerts_below_condition_does_not_trigger_when_price_above_target() {
        Long userId = 1L;
        Alert activeAlert = alert(1L, user(userId), "THYAO", ConditionType.BELOW, new BigDecimal("90.00"), AlertStatus.ACTIVE);

        when(alertRepository.findByStatus(AlertStatus.ACTIVE)).thenReturn(List.of(activeAlert));
        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.of(snapshot("THYAO", new BigDecimal("95.00"))));

        alertService.evaluateActiveAlerts();

        assertThat(activeAlert.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        verify(alertRepository, never()).saveAll(anyList());
    }

    @Test
    void evaluateAlerts_limits_triggered_history_to_10_per_user_and_removes_oldest() {
        Long userId = 1L;
        Alert activeAlert = alert(1L, user(userId), "THYAO", ConditionType.ABOVE, new BigDecimal("100.00"), AlertStatus.ACTIVE);

        List<Alert> history = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            history.add(alert((long) (100 + i), user(userId), "THYAO", ConditionType.ABOVE, new BigDecimal("100.00"), AlertStatus.TRIGGERED));
        }

        when(alertRepository.findByStatus(AlertStatus.ACTIVE)).thenReturn(List.of(activeAlert));
        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.of(snapshot("THYAO", new BigDecimal("110.00"))));
        when(alertRepository.findByUserIdAndStatusOrderByTriggeredAtDesc(userId, AlertStatus.TRIGGERED))
                .thenReturn(history);
        when(cacheManager.getCache(anyString())).thenReturn(null);

        alertService.evaluateActiveAlerts();

        ArgumentCaptor<List<Alert>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(alertRepository).deleteAll(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User user(Long id) {
        return User.builder().id(id).keycloakId("user-" + id).fullName("Test User").email("test@test.com").build();
    }

    private CreateAlertRequest request(String instrumentCode, ConditionType conditionType, BigDecimal targetPrice) {
        CreateAlertRequest req = new CreateAlertRequest();
        req.setInstrumentCode(instrumentCode);
        req.setConditionType(conditionType);
        req.setTargetPrice(targetPrice);
        return req;
    }

    private Alert alert(Long id, User user, String instrumentCode, ConditionType conditionType,
                        BigDecimal targetPrice, AlertStatus status) {
        return Alert.builder()
                .id(id)
                .user(user)
                .instrumentCode(instrumentCode)
                .conditionType(conditionType)
                .targetPrice(targetPrice)
                .status(status)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private MarketQueryService.MarketSnapshot snapshot(String symbol, BigDecimal price) {
        return new MarketQueryService.MarketSnapshot(
                symbol, symbol, price, BigDecimal.ZERO,
                "BIST", "STOCK", "TRY", LocalDateTime.now(),
                null, null, null, null, null);
    }
}
