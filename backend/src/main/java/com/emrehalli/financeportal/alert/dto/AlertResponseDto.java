package com.emrehalli.financeportal.alert.dto;

import com.emrehalli.financeportal.alert.enums.AlertStatus;
import com.emrehalli.financeportal.alert.enums.ConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Fiyat alarmı yanıt modeli")
public class AlertResponseDto {

    @Schema(description = "Alarm ID", example = "1")
    private Long id;

    @Schema(description = "Kullanıcı ID", example = "1")
    private Long userId;

    @Schema(description = "Enstrüman kodu", example = "THYAO")
    private String instrumentCode;

    @Schema(description = "Koşul tipi", example = "ABOVE")
    private ConditionType conditionType;

    @Schema(description = "Hedef fiyat", example = "220.00")
    private BigDecimal targetPrice;

    @Schema(description = "Alarm durumu", example = "ACTIVE")
    private AlertStatus status;

    @Schema(description = "Tetiklenme zamanı")
    private LocalDateTime triggeredAt;

    @Schema(description = "Oluşturulma tarihi")
    private LocalDateTime createdAt;
}

