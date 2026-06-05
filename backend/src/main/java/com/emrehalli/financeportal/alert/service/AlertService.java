package com.emrehalli.financeportal.alert.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.alert.dto.AlertResponseDto;
import com.emrehalli.financeportal.alert.dto.CreateAlertRequest;
import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.enums.AlertStatus;
import com.emrehalli.financeportal.alert.enums.ConditionType;
import com.emrehalli.financeportal.alert.repository.AlertRepository;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final MarketQueryService marketQueryService;
    private final NotificationService notificationService;
    private final CacheManager cacheManager;

    public AlertService(AlertRepository alertRepository,
                        UserRepository userRepository,
                        MarketQueryService marketQueryService,
                        NotificationService notificationService,
                        CacheManager cacheManager) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.marketQueryService = marketQueryService;
        this.notificationService = notificationService;
        this.cacheManager = cacheManager;
    }

    @CacheEvict(cacheNames = "user_alerts", key = "#userId")
    @Transactional
    public AlertResponseDto createAlert(Long userId, CreateAlertRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String normalizedCode = normalizeSymbol(request.getInstrumentCode());

        if (alertRepository.existsByUserIdAndInstrumentCodeIgnoreCaseAndConditionTypeAndTargetPriceAndStatus(
                userId,
                normalizedCode,
                request.getConditionType(),
                request.getTargetPrice(),
                AlertStatus.ACTIVE)) {
            throw new DuplicateResourceException("An active alert already exists for this symbol and condition");
        }

        Alert alert = Alert.builder()
                .user(user)
                .instrumentCode(normalizedCode)
                .conditionType(request.getConditionType())
                .targetPrice(request.getTargetPrice())
                .status(AlertStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Alert saved = alertRepository.save(alert);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "user_alerts",
            key = "#userId",
            unless = "#result.isEmpty()"
    )
    public List<AlertResponseDto> getUserAlerts(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        return alertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @CacheEvict(cacheNames = "user_alerts", key = "#userId")
    @Transactional
    public void cancelAlert(Long userId, Long alertId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        Alert alert = alertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        if (alert.getStatus() == AlertStatus.CANCELLED) {
            throw new BadRequestException("Alert is already cancelled");
        }

        alert.setStatus(AlertStatus.CANCELLED);
        alertRepository.save(alert);
    }

    @Transactional
    public void evaluateActiveAlerts() {
        List<Alert> activeAlerts = alertRepository.findByStatus(AlertStatus.ACTIVE);
        if (activeAlerts.isEmpty()) {
            return;
        }

        Map<String, List<Alert>> bySymbol = activeAlerts.stream()
                .collect(Collectors.groupingBy(Alert::getInstrumentCode));

        List<Alert> triggered = new ArrayList<>();

        for (Map.Entry<String, List<Alert>> entry : bySymbol.entrySet()) {
            var snapshot = marketQueryService.findBySymbol(entry.getKey()).orElse(null);
            if (snapshot == null || snapshot.price() == null) {
                continue;
            }

            BigDecimal currentPrice = snapshot.price();

            for (Alert alert : entry.getValue()) {
                boolean conditionMet = alert.getConditionType() == ConditionType.ABOVE
                        ? currentPrice.compareTo(alert.getTargetPrice()) >= 0
                        : currentPrice.compareTo(alert.getTargetPrice()) <= 0;

                if (conditionMet) {
                    alert.setStatus(AlertStatus.TRIGGERED);
                    alert.setTriggeredAt(LocalDateTime.now());
                    triggered.add(alert);
                }
            }
        }

        if (triggered.isEmpty()) {
            return;
        }

        alertRepository.saveAll(triggered);

        var userAlertsCache = cacheManager.getCache("user_alerts");
        for (Alert alert : triggered) {
            notificationService.createPriceAlertNotification(
                    alert.getUser(),
                    buildNotificationTitle(alert),
                    buildNotificationMessage(alert)
            );
            if (userAlertsCache != null) {
                userAlertsCache.evict(alert.getUser().getId());
            }
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("instrumentCode cannot be blank");
        }
        return symbol.trim()
                .replaceAll("[^A-Za-z0-9:_\\-]", "")
                .toUpperCase();
    }

    private String buildNotificationTitle(Alert alert) {
        return "Fiyat Alarmı: " + alert.getInstrumentCode();
    }

    private String buildNotificationMessage(Alert alert) {
        String direction = alert.getConditionType() == ConditionType.ABOVE ? "üzerine çıktı" : "altına düştü";
        return alert.getInstrumentCode() + " hedef fiyat " + alert.getTargetPrice() + " " + direction + ".";
    }

    private AlertResponseDto toResponse(Alert alert) {
        return AlertResponseDto.builder()
                .id(alert.getId())
                .userId(alert.getUser().getId())
                .instrumentCode(alert.getInstrumentCode())
                .conditionType(alert.getConditionType())
                .targetPrice(alert.getTargetPrice())
                .status(alert.getStatus())
                .triggeredAt(alert.getTriggeredAt())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
