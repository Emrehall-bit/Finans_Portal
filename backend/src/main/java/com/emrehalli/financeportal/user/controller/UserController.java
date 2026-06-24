package com.emrehalli.financeportal.user.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Kullanıcı", description = "Kullanıcı self-servis profil işlemleri")
public class UserController {

    private final UserService userService;
    private final AppMessageSource appMessageSource;

    public UserController(UserService userService, AppMessageSource appMessageSource) {
        this.userService = userService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Mevcut kullanıcı profilini getir", description = "Kimlik doğrulaması yapılmış kullanıcının profil bilgilerini döner")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profil başarıyla getirildi")
    })
    @GetMapping("/me")
    public ApiResponse<UserProfileResponseDto> getCurrentUserProfile() {
        UserProfileResponseDto profile = userService.getCurrentUserProfile();

        return ApiResponse.<UserProfileResponseDto>builder()
                .success(true)
                .data(profile)
                .message(appMessageSource.get("user.profile.fetched"))
                .build();
    }

    @Operation(summary = "Mevcut kullanıcı profilini güncelle", description = "Kimlik doğrulaması yapılmış kullanıcının profil bilgilerini günceller")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profil başarıyla güncellendi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz istek verisi")
    })
    @PutMapping("/me")
    public ApiResponse<UserProfileResponseDto> updateCurrentUserProfile(@Valid @RequestBody UpdateUserRequest request) {
        UserProfileResponseDto profile = userService.updateCurrentUserProfile(request);

        return ApiResponse.<UserProfileResponseDto>builder()
                .success(true)
                .data(profile)
                .message(appMessageSource.get("user.profile.updated"))
                .build();
    }

    @Operation(summary = "Mevcut kullanıcı hesabını sil", description = "Kimlik doğrulaması yapılmış kullanıcının hesabını kalıcı olarak siler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hesap başarıyla silindi")
    })
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteCurrentUserAccount() {
        userService.deleteCurrentUserAccount();

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Account deleted successfully")
                .build();
    }
}

