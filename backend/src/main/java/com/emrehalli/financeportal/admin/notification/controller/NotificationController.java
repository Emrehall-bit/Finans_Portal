package com.emrehalli.financeportal.admin.notification.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.admin.notification.dto.NotificationResponseDto;
import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AppMessageSource appMessageSource;

    public NotificationController(NotificationService notificationService, AppMessageSource appMessageSource) {
        this.notificationService = notificationService;
        this.appMessageSource = appMessageSource;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponseDto>> getCurrentUserNotifications() {
        return ApiResponse.<List<NotificationResponseDto>>builder()
                .success(true)
                .data(notificationService.getCurrentUserNotifications())
                .message(appMessageSource.get("notification.list.fetched"))
                .build();
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponseDto> markAsRead(@PathVariable Long id) {
        return ApiResponse.<NotificationResponseDto>builder()
                .success(true)
                .data(notificationService.markAsRead(id))
                .message(appMessageSource.get("notification.read"))
                .build();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("notification.read.all"))
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount() {
        return ApiResponse.<Map<String, Long>>builder()
                .success(true)
                .data(Map.of("count", notificationService.getUnreadCount()))
                .message(appMessageSource.get("notification.unread.count.fetched"))
                .build();
    }
}



