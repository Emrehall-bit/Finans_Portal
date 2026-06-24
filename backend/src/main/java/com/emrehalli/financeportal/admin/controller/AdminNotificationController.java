package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.admin.notification.dto.CreateBroadcastNotificationRequest;
import com.emrehalli.financeportal.admin.notification.dto.CreateUserNotificationRequest;
import com.emrehalli.financeportal.admin.notification.dto.NotificationResponseDto;
import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@Tag(name = "Admin - Bildirimler", description = "Yonetici bildirim gonderimi")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final AppMessageSource appMessageSource;

    public AdminNotificationController(NotificationService notificationService, AppMessageSource appMessageSource) {
        this.notificationService = notificationService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Kullaniciya bildirim gonder", description = "Belirli bir kullaniciya yonetici bildirimi gonderir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bildirim basariyla gonderildi"))
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

    @Operation(summary = "Toplu duyuru gonder", description = "Tum kullanici tabanina genel duyuru bildirimi gonderir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Duyuru basariyla gonderildi"))
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

