package com.emrehalli.financeportal.admin.moderation.dto;

import com.emrehalli.financeportal.admin.moderation.enums.ModerationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationResponseDto {

    private Long id;
    private Long userId;
    private ModerationStatus status;
    private String reason;
    private LocalDateTime blockedUntil;
    private Long createdBy;
    private LocalDateTime createdAt;
}



