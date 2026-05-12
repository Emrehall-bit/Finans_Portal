package com.emrehalli.financeportal.notification.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.notification.dto.CreateBroadcastNotificationRequest;
import com.emrehalli.financeportal.notification.dto.CreateUserNotificationRequest;
import com.emrehalli.financeportal.notification.dto.NotificationResponseDto;
import com.emrehalli.financeportal.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final AppMessageSource appMessageSource;

    public AdminNotificationController(NotificationService notificationService, AppMessageSource appMessageSource) {
        this.notificationService = notificationService;
        this.appMessageSource = appMessageSource;
    }

    @PostMapping("/user/{userId}")
    public ApiResponse<NotificationResponseDto> sendNotificationToUser(
            @PathVariable Long userId,
            @Valid @RequestBody CreateUserNotificationRequest request
    ) {
        return ApiResponse.<NotificationResponseDto>builder()
                .success(true)
                .data(notificationService.createAdminNotificationForUser(userId, request))
                .message(appMessageSource.get("notification.admin.sent"))
                .build();
    }

    @PostMapping("/broadcast")
    public ApiResponse<NotificationResponseDto> sendBroadcastNotification(
            @Valid @RequestBody CreateBroadcastNotificationRequest request
    ) {
        return ApiResponse.<NotificationResponseDto>builder()
                .success(true)
                .data(notificationService.createBroadcastNotification(request))
                .message(appMessageSource.get("notification.broadcast.sent"))
                .build();
    }
}
