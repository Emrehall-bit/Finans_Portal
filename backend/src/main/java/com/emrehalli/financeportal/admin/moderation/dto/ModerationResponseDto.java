package com.emrehalli.financeportal.admin.moderation.dto;

import com.emrehalli.financeportal.admin.moderation.enums.ModerationStatus;
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
@Schema(description = "Kullanıcı moderasyon durumu yanıt modeli")
public class ModerationResponseDto {

    @Schema(description = "Moderasyon kaydı ID", example = "1")
    private Long id;

    @Schema(description = "Kullanıcı ID", example = "5")
    private Long userId;

    @Schema(description = "Moderasyon durumu", example = "TEMP_BLOCKED")
    private ModerationStatus status;

    @Schema(description = "Engelleme gerekçesi", example = "Spam içerik paylaşımı")
    private String reason;

    @Schema(description = "Engel bitiş tarihi (geçici engeller için)")
    private LocalDateTime blockedUntil;

    @Schema(description = "İşlemi yapan admin ID", example = "1")
    private Long createdBy;

    @Schema(description = "İşlem tarihi")
    private LocalDateTime createdAt;
}

