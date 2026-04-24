package com.emrehalli.financeportal.alert.repository;

import com.emrehalli.financeportal.alert.entity.Alert;
import com.emrehalli.financeportal.alert.enums.AlertStatus;
import com.emrehalli.financeportal.alert.enums.ConditionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @EntityGraph(attributePaths = "user")
    List<Alert> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    List<Alert> findByStatus(AlertStatus status);

    @EntityGraph(attributePaths = "user")
    Optional<Alert> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndInstrumentCodeIgnoreCaseAndConditionTypeAndTargetPriceAndStatus(
            Long userId,
            String instrumentCode,
            ConditionType conditionType,
            BigDecimal targetPrice,
            AlertStatus status
    );
}



