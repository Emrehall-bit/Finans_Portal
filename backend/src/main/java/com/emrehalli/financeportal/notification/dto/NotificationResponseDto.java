package com.emrehalli.financeportal.notification.dto;

import com.emrehalli.financeportal.notification.enums.NotificationTargetType;
import com.emrehalli.financeportal.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDto {

    private Long id;
    private Long userId;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationTargetType targetType;
    private boolean read;
    private LocalDateTime createdAt;
}
