package com.emrehalli.financeportal.admin.notification.repository;

import com.emrehalli.financeportal.admin.notification.entity.Notification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    List<Notification> findByUserIdAndReadFalse(Long userId);

    @Query("""
            select n
            from Notification n
            left join n.user u
            where u.id = :userId
               or n.targetType = com.emrehalli.financeportal.admin.notification.enums.NotificationTargetType.BROADCAST
            order by n.createdAt desc
            """)
    List<Notification> findVisibleNotificationsOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("""
            select n
            from Notification n
            left join n.user u
            where n.id = :notificationId
              and (
                u.id = :userId
                or n.targetType = com.emrehalli.financeportal.admin.notification.enums.NotificationTargetType.BROADCAST
              )
            """)
    Optional<Notification> findVisibleNotificationById(@Param("notificationId") Long notificationId, @Param("userId") Long userId);
}

