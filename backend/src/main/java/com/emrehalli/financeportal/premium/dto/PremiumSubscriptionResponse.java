package com.emrehalli.financeportal.premium.dto;

import com.emrehalli.financeportal.premium.entity.PremiumSubscription;
import com.emrehalli.financeportal.premium.entity.PremiumSubscriptionStatus;
import com.emrehalli.financeportal.user.entity.UserRole;

import java.time.LocalDateTime;

public record PremiumSubscriptionResponse(
        Long subscriptionId,
        Long userId,
        String userFullName,
        String planType,
        PremiumSubscriptionStatus status,
        Long processInstanceId,
        UserRole effectiveRole,
        boolean premiumActive,
        LocalDateTime startedAt,
        LocalDateTime requestedAt,
        LocalDateTime activatedAt,
        LocalDateTime expiresAt,
        LocalDateTime cancelledAt,
        LocalDateTime updatedAt
) {
    public static PremiumSubscriptionResponse from(PremiumSubscription subscription, UserRole effectiveRole) {
        LocalDateTime activatedAt = subscription.getActivatedAt();
        return new PremiumSubscriptionResponse(
                subscription.getId(),
                subscription.getUser() != null ? subscription.getUser().getId() : null,
                subscription.getUser() != null ? subscription.getUser().getFullName() : null,
                "PREMIUM",
                subscription.getStatus(),
                subscription.getProcessInstanceId(),
                effectiveRole,
                effectiveRole == UserRole.USER_PREMIUM || effectiveRole == UserRole.ADMIN,
                subscription.getRequestedAt(),
                subscription.getRequestedAt(),
                activatedAt,
                activatedAt != null ? activatedAt.plusDays(30) : null,
                subscription.getCancelledAt(),
                subscription.getUpdatedAt()
        );
    }

    public static PremiumSubscriptionResponse currentRoleOnly(UserRole effectiveRole) {
        return new PremiumSubscriptionResponse(
                null,
                null,
                null,
                "PREMIUM",
                effectiveRole == UserRole.USER_PREMIUM || effectiveRole == UserRole.ADMIN
                        ? PremiumSubscriptionStatus.ACTIVE
                        : null,
                null,
                effectiveRole,
                effectiveRole == UserRole.USER_PREMIUM || effectiveRole == UserRole.ADMIN,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
