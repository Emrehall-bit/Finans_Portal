package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.admin.moderation.dto.ModerationResponseDto;
import com.emrehalli.financeportal.admin.moderation.dto.PermBlockUserRequest;
import com.emrehalli.financeportal.admin.moderation.dto.TempBlockUserRequest;
import com.emrehalli.financeportal.admin.moderation.dto.UnblockUserRequest;
import com.emrehalli.financeportal.admin.moderation.service.UserModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Kullanici Moderasyonu", description = "Kullanici engelleme ve moderasyon islemleri")
public class AdminUserModerationController {

    private final UserModerationService userModerationService;
    private final AppMessageSource appMessageSource;

    public AdminUserModerationController(UserModerationService userModerationService, AppMessageSource appMessageSource) {
        this.userModerationService = userModerationService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Gecici engel uygula", description = "Kullaniciya sureli engel uygulayarak erisimini askiya alir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gecici engel basariyla uygulandi"))
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

    @Operation(summary = "Kalici engel uygula", description = "Kullaniciya suresiz engel uygulayarak erisimini kalici olarak askiya alir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kalici engel basariyla uygulandi"))
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

    @Operation(summary = "Engeli kaldir", description = "Kullanicinin aktif engelini kaldirarak erisimini yeniden saglar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Engel basariyla kaldirildi"))
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

    @Operation(summary = "Moderasyon durumunu getir", description = "Kullanicinin mevcut moderasyon durumunu sorgular")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Moderasyon durumu basariyla getirildi"))
    @GetMapping("/{userId}/moderation")
    public ApiResponse<ModerationResponseDto> getCurrentModeration(@PathVariable Long userId) {
        return ApiResponse.<ModerationResponseDto>builder()
                .success(true)
                .data(userModerationService.getCurrentModeration(userId))
                .message(appMessageSource.get("moderation.fetched"))
                .build();
    }
}

