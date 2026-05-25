package com.emrehalli.financeportal.admin.notification.repository;

import com.emrehalli.financeportal.admin.notification.entity.Notification;
import com.emrehalli.financeportal.admin.notification.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    boolean existsByNotificationIdAndUserId(Long notificationId, Long userId);

    Optional<NotificationRead> findByNotificationIdAndUserId(Long notificationId, Long userId);

    @Query("""
            select nr.notification.id
            from NotificationRead nr
            where nr.user.id = :userId and nr.notification.id in :notificationIds
            """)
    List<Long> findReadNotificationIdsByUserIdAndNotificationIds(
            @Param("userId") Long userId,
            @Param("notificationIds") Collection<Long> notificationIds
    );

    @Query("""
            select count(n)
            from Notification n
            where n.targetType = com.emrehalli.financeportal.admin.notification.enums.NotificationTargetType.BROADCAST
              and not exists (
                select 1
                from NotificationRead nr
                where nr.notification = n and nr.user.id = :userId
              )
            """)
    long countUnreadBroadcastNotifications(@Param("userId") Long userId);

    @Query("""
            select n
            from Notification n
            where n.targetType = com.emrehalli.financeportal.admin.notification.enums.NotificationTargetType.BROADCAST
              and not exists (
                select 1
                from NotificationRead nr
                where nr.notification = n and nr.user.id = :userId
              )
            """)
    List<Notification> findUnreadBroadcastNotifications(@Param("userId") Long userId);
}



