package com.emrehalli.financeportal.admin.dto;

import com.emrehalli.financeportal.admin.enums.AdminAuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLogResponseDto {

    private Long id;
    private Long actorUserId;
    private Long targetUserId;
    private AdminAuditAction action;
    private String description;
    private String metadata;
    private LocalDateTime createdAt;
}



