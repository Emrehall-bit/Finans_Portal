package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.moderation.dto.ModerationResponseDto;
import com.emrehalli.financeportal.moderation.dto.PermBlockUserRequest;
import com.emrehalli.financeportal.moderation.dto.TempBlockUserRequest;
import com.emrehalli.financeportal.moderation.dto.UnblockUserRequest;
import com.emrehalli.financeportal.moderation.service.UserModerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserModerationController {

    private final UserModerationService userModerationService;
    private final AppMessageSource appMessageSource;

    public AdminUserModerationController(UserModerationService userModerationService, AppMessageSource appMessageSource) {
        this.userModerationService = userModerationService;
        this.appMessageSource = appMessageSource;
    }

    @PatchMapping("/{userId}/temp-block")
    public ApiResponse<ModerationResponseDto> tempBlockUser(
            @PathVariable Long userId,
            @Valid @RequestBody TempBlockUserRequest request
    ) {
        return ApiResponse.<ModerationResponseDto>builder()
                .success(true)
                .data(userModerationService.tempBlockUser(userId, request))
                .message(appMessageSource.get("moderation.temp.blocked"))
                .build();
    }

    @PatchMapping("/{userId}/perm-block")
    public ApiResponse<ModerationResponseDto> permBlockUser(
            @PathVariable Long userId,
            @Valid @RequestBody PermBlockUserRequest request
    ) {
        return ApiResponse.<ModerationResponseDto>builder()
                .success(true)
                .data(userModerationService.permBlockUser(userId, request))
                .message(appMessageSource.get("moderation.perm.blocked"))
                .build();
    }

    @PatchMapping("/{userId}/unblock")
    public ApiResponse<ModerationResponseDto> unblockUser(
            @PathVariable Long userId,
            @Valid @RequestBody UnblockUserRequest request
    ) {
        return ApiResponse.<ModerationResponseDto>builder()
                .success(true)
                .data(userModerationService.unblockUser(userId, request))
                .message(appMessageSource.get("moderation.unblocked"))
                .build();
    }

    @GetMapping("/{userId}/moderation")
    public ApiResponse<ModerationResponseDto> getCurrentModeration(@PathVariable Long userId) {
        return ApiResponse.<ModerationResponseDto>builder()
                .success(true)
                .data(userModerationService.getCurrentModeration(userId))
                .message(appMessageSource.get("moderation.fetched"))
                .build();
    }
}
