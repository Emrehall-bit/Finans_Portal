package com.emrehalli.financeportal.alert.dto;

import com.emrehalli.financeportal.alert.enums.AlertStatus;
import com.emrehalli.financeportal.alert.enums.ConditionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class AlertResponseDto {

    private Long id;
    private Long userId;
    private String instrumentCode;
    private ConditionType conditionType;
    private BigDecimal targetPrice;
    private AlertStatus status;
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;
    private BigDecimal currentPrice;
    private String source;
    private String lastUpdated;
}



