package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.admin.dto.AdminAuditLogResponseDto;
import com.emrehalli.financeportal.admin.enums.AdminAuditAction;
import com.emrehalli.financeportal.admin.service.AdminAuditLogService;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;
    private final AppMessageSource appMessageSource;

    public AdminAuditLogController(AdminAuditLogService adminAuditLogService, AppMessageSource appMessageSource) {
        this.adminAuditLogService = adminAuditLogService;
        this.appMessageSource = appMessageSource;
    }

    @GetMapping
    public ApiResponse<Page<AdminAuditLogResponseDto>> getAuditLogs(
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.<Page<AdminAuditLogResponseDto>>builder()
                .success(true)
                .data(adminAuditLogService.getAuditLogs(action, targetUserId, actorUserId, page, size))
                .message(appMessageSource.get("audit.logs.fetched"))
                .build();
    }
}
