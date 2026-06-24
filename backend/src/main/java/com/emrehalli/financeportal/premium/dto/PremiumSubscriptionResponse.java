package com.emrehalli.financeportal.premium.dto;

import com.emrehalli.financeportal.premium.entity.PremiumSubscription;
import com.emrehalli.financeportal.premium.entity.PremiumSubscriptionStatus;
import com.emrehalli.financeportal.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Premium abonelik durumu yanıt modeli")
public record PremiumSubscriptionResponse(
        @Schema(description = "Abonelik ID", example = "1") Long subscriptionId,
        @Schema(description = "Kullanıcı ID", example = "1") Long userId,
        @Schema(description = "Kullanıcı adı", example = "Emre Halli") String userFullName,
        @Schema(description = "Plan tipi", example = "PREMIUM") String planType,
        @Schema(description = "Abonelik durumu", example = "ACTIVE") PremiumSubscriptionStatus status,
        @Schema(description = "jBPM süreç kimliği") Long processInstanceId,
        @Schema(description = "Etkin kullanıcı rolü", example = "USER_PREMIUM") UserRole effectiveRole,
        @Schema(description = "Premium aktif mi", example = "true") boolean premiumActive,
        @Schema(description = "Başlangıç tarihi") LocalDateTime startedAt,
        @Schema(description = "Talep tarihi") LocalDateTime requestedAt,
        @Schema(description = "Aktivasyon tarihi") LocalDateTime activatedAt,
        @Schema(description = "Son kullanma tarihi") LocalDateTime expiresAt,
        @Schema(description = "İptal tarihi") LocalDateTime cancelledAt,
        @Schema(description = "Güncelleme tarihi") LocalDateTime updatedAt
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

