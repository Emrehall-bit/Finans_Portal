package com.emrehalli.financeportal.admin.notification.dto;

import com.emrehalli.financeportal.admin.notification.enums.NotificationTargetType;
import com.emrehalli.financeportal.admin.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Bildirim yanıt modeli")
public class NotificationResponseDto {

    @Schema(description = "Bildirim ID", example = "1")
    private Long id;

    @Schema(description = "Hedef kullanıcı ID", example = "1")
    private Long userId;

    @Schema(description = "Bildirim başlığı", example = "Premium aboneliğiniz aktive edildi")
    private String title;

    @Schema(description = "Bildirim mesajı", example = "Premium özellikleriniz 30 gün boyunca kullanılabilir")
    private String message;

    @Schema(description = "Bildirim tipi", example = "INFO")
    private NotificationType type;

    @Schema(description = "Hedef tipi", example = "USER")
    private NotificationTargetType targetType;

    @Schema(description = "Okundu mu", example = "false")
    private boolean read;

    @Schema(description = "Oluşturulma tarihi")
    private LocalDateTime createdAt;
}

