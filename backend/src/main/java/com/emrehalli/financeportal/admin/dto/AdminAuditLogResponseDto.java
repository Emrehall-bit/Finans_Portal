package com.emrehalli.financeportal.admin.dto;

import com.emrehalli.financeportal.admin.enums.AdminAuditAction;
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
@Schema(description = "Admin denetim logu yanıt modeli")
public class AdminAuditLogResponseDto {

    @Schema(description = "Log ID", example = "1")
    private Long id;

    @Schema(description = "İşlemi yapan admin ID", example = "1")
    private Long actorUserId;

    @Schema(description = "Hedef kullanıcı ID", example = "5")
    private Long targetUserId;

    @Schema(description = "İşlem türü", example = "USER_ROLE_UPDATED")
    private AdminAuditAction action;

    @Schema(description = "İşlem açıklaması", example = "Kullanıcı rolü USER_PREMIUM olarak güncellendi")
    private String description;

    @Schema(description = "Ek bilgi (JSON)", example = "{\"oldRole\":\"USER\",\"newRole\":\"USER_PREMIUM\"}")
    private String metadata;

    @Schema(description = "Oluşturulma tarihi")
    private LocalDateTime createdAt;
}

