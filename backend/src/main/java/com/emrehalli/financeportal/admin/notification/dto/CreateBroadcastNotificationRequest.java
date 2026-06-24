package com.emrehalli.financeportal.admin.notification.dto;

import com.emrehalli.financeportal.admin.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Toplu duyuru bildirimi oluşturma isteği")
public class CreateBroadcastNotificationRequest {

    @NotBlank(message = "Title is required")
    @Schema(description = "Bildirim başlığı", example = "Sistem bakımı", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "Message is required")
    @Schema(description = "Bildirim mesajı", example = "24 Haziran saat 02:00-04:00 arası planlı bakım yapılacaktır", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Bildirim tipi", example = "ANNOUNCEMENT")
    private NotificationType type;
}

