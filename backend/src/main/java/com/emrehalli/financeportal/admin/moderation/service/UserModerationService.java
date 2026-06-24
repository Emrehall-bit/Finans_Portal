package com.emrehalli.financeportal.admin.moderation.service;

import com.emrehalli.financeportal.admin.enums.AdminAuditAction;
import com.emrehalli.financeportal.admin.moderation.dto.ModerationResponseDto;
import com.emrehalli.financeportal.admin.moderation.dto.PermBlockUserRequest;
import com.emrehalli.financeportal.admin.moderation.dto.TempBlockUserRequest;
import com.emrehalli.financeportal.admin.moderation.dto.UnblockUserRequest;
import com.emrehalli.financeportal.admin.moderation.entity.UserModeration;
import com.emrehalli.financeportal.admin.moderation.enums.ModerationStatus;
import com.emrehalli.financeportal.admin.moderation.repository.UserModerationRepository;
import com.emrehalli.financeportal.admin.service.AdminAuditLogService;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class UserModerationService {

    private final UserModerationRepository userModerationRepository;
    private final UserService userService;
    private final AdminAuditLogService adminAuditLogService;

    public UserModerationService(
            UserModerationRepository userModerationRepository,
            UserService userService,
            AdminAuditLogService adminAuditLogService
    ) {
        this.userModerationRepository = userModerationRepository;
        this.userService = userService;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional
    public ModerationResponseDto tempBlockUser(Long userId, TempBlockUserRequest request) {
        User targetUser = userService.getUserEntityById(userId);
        User currentAdmin = userService.getCurrentAuthenticatedUserEntity();

        ensureAdminCannotModerateSelf(targetUser, currentAdmin);
        validateBlockedUntil(request.getBlockedUntil());

        UserModeration moderation = UserModeration.builder()
                .user(targetUser)
                .status(ModerationStatus.TEMP_BLOCKED)
                .reason(normalizeReason(request.getReason()))
                .blockedUntil(request.getBlockedUntil())
                .createdBy(currentAdmin)
                .build();

        UserModeration savedModeration = userModerationRepository.save(moderation);
        adminAuditLogService.log(
                currentAdmin.getId(),
                targetUser.getId(),
                AdminAuditAction.USER_TEMP_BLOCKED,
                "User temporarily blocked by admin",
                "blockedUntil=" + request.getBlockedUntil()
        );
        return toResponse(savedModeration);
    }

    @Transactional
    public ModerationResponseDto permBlockUser(Long userId, PermBlockUserRequest request) {
        User targetUser = userService.getUserEntityById(userId);
        User currentAdmin = userService.getCurrentAuthenticatedUserEntity();

        ensureAdminCannotModerateSelf(targetUser, currentAdmin);

        UserModeration moderation = UserModeration.builder()
                .user(targetUser)
                .status(ModerationStatus.PERM_BLOCKED)
                .reason(normalizeReason(request.getReason()))
                .blockedUntil(null)
                .createdBy(currentAdmin)
                .build();

        UserModeration savedModeration = userModerationRepository.save(moderation);
        adminAuditLogService.log(
                currentAdmin.getId(),
                targetUser.getId(),
                AdminAuditAction.USER_PERM_BLOCKED,
                "User permanently blocked by admin",
                null
        );
        return toResponse(savedModeration);
    }

    @Transactional
    public ModerationResponseDto unblockUser(Long userId, UnblockUserRequest request) {
        User targetUser = userService.getUserEntityById(userId);
        User currentAdmin = userService.getCurrentAuthenticatedUserEntity();

        ensureAdminCannotModerateSelf(targetUser, currentAdmin);

        UserModeration moderation = UserModeration.builder()
                .user(targetUser)
                .status(ModerationStatus.ACTIVE)
                .reason(normalizeReason(request.getReason()))
                .blockedUntil(null)
                .createdBy(currentAdmin)
                .build();

        UserModeration savedModeration = userModerationRepository.save(moderation);
        adminAuditLogService.log(
                currentAdmin.getId(),
                targetUser.getId(),
                AdminAuditAction.USER_UNBLOCKED,
                "User moderation removed by admin",
                null
        );
        return toResponse(savedModeration);
    }

    @Transactional(readOnly = true)
    public ModerationResponseDto getCurrentModeration(Long userId) {
        User targetUser = userService.getUserEntityById(userId);

        return userModerationRepository.findTopByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(this::toResponse)
                .orElseGet(() -> ModerationResponseDto.builder()
                        .id(null)
                        .userId(targetUser.getId())
                        .status(ModerationStatus.ACTIVE)
                        .reason(null)
                        .blockedUntil(null)
                        .createdBy(null)
                        .createdAt(null)
                        .build());
    }

    @Transactional(readOnly = true)
    public void assertAccessAllowed(Long userId) {
        userModerationRepository.findTopByUserIdOrderByCreatedAtDescIdDesc(userId)
                .ifPresent(this::validateLatestModerationForAccess);
    }

    private void ensureAdminCannotModerateSelf(User targetUser, User currentAdmin) {
        if (targetUser.getId() != null && targetUser.getId().equals(currentAdmin.getId())) {
            throw new BadRequestException("Admin users cannot moderate their own account");
        }
    }

    private void validateBlockedUntil(LocalDateTime blockedUntil) {
        if (blockedUntil == null) {
            throw new BadRequestException("blockedUntil is required");
        }

        if (!blockedUntil.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("blockedUntil must be a future date");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }

        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateLatestModerationForAccess(UserModeration moderation) {
        if (moderation.getStatus() == ModerationStatus.PERM_BLOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is permanently blocked");
        }

        if (moderation.getStatus() == ModerationStatus.TEMP_BLOCKED
                && moderation.getBlockedUntil() != null
                && moderation.getBlockedUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is temporarily blocked");
        }
    }

    private ModerationResponseDto toResponse(UserModeration moderation) {
        return ModerationResponseDto.builder()
                .id(moderation.getId())
                .userId(moderation.getUser().getId())
                .status(moderation.getStatus())
                .reason(moderation.getReason())
                .blockedUntil(moderation.getBlockedUntil())
                .createdBy(moderation.getCreatedBy() != null ? moderation.getCreatedBy().getId() : null)
                .createdAt(moderation.getCreatedAt())
                .build();
    }
}

