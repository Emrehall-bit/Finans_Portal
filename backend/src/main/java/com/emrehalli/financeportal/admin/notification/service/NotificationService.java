package com.emrehalli.financeportal.admin.notification.service;

import com.emrehalli.financeportal.admin.enums.AdminAuditAction;
import com.emrehalli.financeportal.admin.notification.dto.CreateBroadcastNotificationRequest;
import com.emrehalli.financeportal.admin.notification.dto.CreateUserNotificationRequest;
import com.emrehalli.financeportal.admin.notification.dto.NotificationResponseDto;
import com.emrehalli.financeportal.admin.notification.entity.Notification;
import com.emrehalli.financeportal.admin.notification.entity.NotificationRead;
import com.emrehalli.financeportal.admin.notification.enums.NotificationTargetType;
import com.emrehalli.financeportal.admin.notification.enums.NotificationType;
import com.emrehalli.financeportal.admin.notification.repository.NotificationReadRepository;
import com.emrehalli.financeportal.admin.notification.repository.NotificationRepository;
import com.emrehalli.financeportal.admin.service.AdminAuditLogService;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final UserService userService;
    private final AdminAuditLogService adminAuditLogService;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationReadRepository notificationReadRepository,
            UserService userService,
            AdminAuditLogService adminAuditLogService
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationReadRepository = notificationReadRepository;
        this.userService = userService;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getCurrentUserNotifications() {
        User currentUser = userService.getCurrentAuthenticatedUserEntity();
        List<Notification> notifications = notificationRepository.findVisibleNotificationsOrderByCreatedAtDesc(currentUser.getId());
        Set<Long> readBroadcastIds = getReadBroadcastIds(currentUser.getId(), notifications);

        return notifications.stream()
                .map(notification -> toResponse(notification, isNotificationRead(notification, readBroadcastIds)))
                .toList();
    }

    @Transactional
    public NotificationResponseDto markAsRead(Long notificationId) {
        User currentUser = userService.getCurrentAuthenticatedUserEntity();
        Notification notification = notificationRepository.findVisibleNotificationById(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (notification.getTargetType() == NotificationTargetType.BROADCAST) {
            createBroadcastReadRecordIfMissing(notification, currentUser);
            return toResponse(notification, true);
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }

        return toResponse(notification, true);
    }

    @Transactional
    public void markAllAsRead() {
        User currentUser = userService.getCurrentAuthenticatedUserEntity();
        List<Notification> notifications = notificationRepository.findByUserIdAndReadFalse(currentUser.getId());

        notifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(notifications);

        List<Notification> unreadBroadcastNotifications = notificationReadRepository.findUnreadBroadcastNotifications(currentUser.getId());
        List<NotificationRead> broadcastReadRecords = unreadBroadcastNotifications.stream()
                .map(notification -> NotificationRead.builder()
                        .notification(notification)
                        .user(currentUser)
                        .build())
                .toList();

        if (!broadcastReadRecords.isEmpty()) {
            notificationReadRepository.saveAll(broadcastReadRecords);
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User currentUser = userService.getCurrentAuthenticatedUserEntity();
        return notificationRepository.countByUserIdAndReadFalse(currentUser.getId())
                + notificationReadRepository.countUnreadBroadcastNotifications(currentUser.getId());
    }

    @Transactional
    public NotificationResponseDto createAdminNotificationForUser(Long userId, CreateUserNotificationRequest request) {
        User currentAdmin = userService.getCurrentAuthenticatedUserEntity();
        User targetUser = userService.getUserEntityById(userId);
        NotificationType type = normalizeAdminNotificationType(request.getType());

        Notification notification = Notification.builder()
                .user(targetUser)
                .title(request.getTitle().trim())
                .message(request.getMessage().trim())
                .type(type)
                .targetType(NotificationTargetType.USER)
                .read(false)
                .createdBy(currentAdmin)
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        adminAuditLogService.log(
                currentAdmin.getId(),
                targetUser.getId(),
                AdminAuditAction.NOTIFICATION_SENT_TO_USER,
                "Admin notification sent to user",
                "type=" + type
        );
        return toResponse(savedNotification, false);
    }

    @Transactional
    public void createSystemBroadcastNotification(String title, String message) {
        Notification notification = Notification.builder()
                .user(null)
                .title(title)
                .message(message)
                .type(NotificationType.SYSTEM)
                .targetType(NotificationTargetType.BROADCAST)
                .read(false)
                .createdBy(null)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public NotificationResponseDto createBroadcastNotification(CreateBroadcastNotificationRequest request) {
        User currentAdmin = userService.getCurrentAuthenticatedUserEntity();
        NotificationType type = normalizeBroadcastNotificationType(request.getType());

        Notification notification = Notification.builder()
                .user(null)
                .title(request.getTitle().trim())
                .message(request.getMessage().trim())
                .type(type)
                .targetType(NotificationTargetType.BROADCAST)
                .read(false)
                .createdBy(currentAdmin)
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        adminAuditLogService.log(
                currentAdmin.getId(),
                null,
                AdminAuditAction.NOTIFICATION_BROADCAST_SENT,
                "Broadcast notification sent by admin",
                "type=" + type
        );
        return toResponse(savedNotification, false);
    }

    private NotificationType normalizeAdminNotificationType(NotificationType type) {
        NotificationType resolvedType = type != null ? type : NotificationType.ADMIN_MESSAGE;
        return switch (resolvedType) {
            case ADMIN_MESSAGE, SECURITY, ANNOUNCEMENT -> resolvedType;
            default -> throw new BadRequestException("Notification type must be ADMIN_MESSAGE, SECURITY, or ANNOUNCEMENT");
        };
    }

    private NotificationType normalizeBroadcastNotificationType(NotificationType type) {
        NotificationType resolvedType = type != null ? type : NotificationType.ANNOUNCEMENT;
        return switch (resolvedType) {
            case ANNOUNCEMENT, SYSTEM, SECURITY -> resolvedType;
            default -> throw new BadRequestException("Notification type must be ANNOUNCEMENT, SYSTEM, or SECURITY");
        };
    }

    private Set<Long> getReadBroadcastIds(Long userId, List<Notification> notifications) {
        List<Long> broadcastIds = notifications.stream()
                .filter(notification -> notification.getTargetType() == NotificationTargetType.BROADCAST)
                .map(Notification::getId)
                .toList();

        if (broadcastIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(notificationReadRepository.findReadNotificationIdsByUserIdAndNotificationIds(userId, broadcastIds));
    }

    private boolean isNotificationRead(Notification notification, Set<Long> readBroadcastIds) {
        if (notification.getTargetType() == NotificationTargetType.BROADCAST) {
            return readBroadcastIds.contains(notification.getId());
        }

        return notification.isRead();
    }

    private void createBroadcastReadRecordIfMissing(Notification notification, User currentUser) {
        if (!notificationReadRepository.existsByNotificationIdAndUserId(notification.getId(), currentUser.getId())) {
            notificationReadRepository.save(NotificationRead.builder()
                    .notification(notification)
                    .user(currentUser)
                    .build());
        }
    }

    private NotificationResponseDto toResponse(Notification notification, boolean read) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .userId(notification.getUser() != null ? notification.getUser().getId() : null)
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .targetType(notification.getTargetType())
                .read(read)
                .createdAt(notification.getCreatedAt())
                .build();
    }
}



