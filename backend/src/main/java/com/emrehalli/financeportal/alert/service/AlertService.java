package com.emrehalli.financeportal.alert.service;

import com.emrehalli.financeportal.alert.dto.AlertResponseDto;
import com.emrehalli.financeportal.alert.dto.CreateAlertRequest;
import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.enums.AlertStatus;
import com.emrehalli.financeportal.alert.repository.AlertRepository;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;

    public AlertService(AlertRepository alertRepository,
                        UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
    }

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
    public List<AlertResponseDto> getUserAlerts(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        return alertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

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
        // Automatic trigger evaluation requires a live price feed; no-op without market quotes.
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("instrumentCode cannot be blank");
        }
        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
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
                .currentPrice(null)
                .source(null)
                .lastUpdated(null)
                .build();
    }
}



