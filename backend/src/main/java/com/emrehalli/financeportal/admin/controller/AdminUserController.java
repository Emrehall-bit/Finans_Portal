package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.admin.dto.AdminUpdateUserRequest;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRoleRequest;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Kullanici Yonetimi", description = "Yonetici kullanici yasam dongusu yonetimi")
public class AdminUserController {

    private final UserService userService;
    private final AppMessageSource appMessageSource;

    public AdminUserController(UserService userService, AppMessageSource appMessageSource) {
        this.userService = userService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Yeni kullanici olustur", description = "Yonetici tarafindan yeni bir kullanici hesabi olusturur")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanici basariyla olusturuldu"))
    @PostMapping
    public ApiResponse<UserResponseDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponseDto user = userService.createUser(request);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.created"))
                .build();
    }

    @Operation(summary = "Kullanicilari listele", description = "Sayfalanmis ve aranabilir kullanici listesini getirir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanici listesi basariyla getirildi"))
    @GetMapping
    public ApiResponse<Page<UserResponseDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ApiResponse.<Page<UserResponseDto>>builder()
                .success(true)
                .data(userService.getAllUsers(page, size, search))
                .message(appMessageSource.get("user.list.fetched"))
                .build();
    }

    @Operation(summary = "Kullanici detayini getir", description = "Belirtilen kullanicinin detay bilgilerini getirir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanici detayi basariyla getirildi"))
    @GetMapping("/{userId}")
    public ApiResponse<UserResponseDto> getUserById(@PathVariable Long userId) {
        UserResponseDto user = userService.getUserById(userId);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.fetched"))
                .build();
    }

    @Operation(summary = "Kullanici bilgilerini guncelle", description = "Yonetici tarafindan kullanici profil bilgilerini gunceller")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanici bilgileri basariyla guncellendi"))
    @PutMapping("/{userId}")
    public ApiResponse<UserResponseDto> updateUserAsAdmin(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        UserResponseDto user = userService.updateUserAsAdmin(userId, request);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.updated"))
                .build();
    }

    @Operation(summary = "Kullaniciyi deaktive et", description = "Kullanici hesabini deaktive ederek erisimini askiya alir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanici basariyla deaktive edildi"))
    @PatchMapping("/{userId}/deactivate")
    public ApiResponse<UserResponseDto> deactivateUser(@PathVariable Long userId) {
        UserResponseDto user = userService.deactivateUser(userId);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.deactivated"))
                .build();
    }

    @Operation(summary = "Kullaniciyi aktive et", description = "Deaktive edilmis kullanici hesabini yeniden aktive eder")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanici basariyla aktive edildi"))
    @PatchMapping("/{userId}/activate")
    public ApiResponse<UserResponseDto> activateUser(@PathVariable Long userId) {
        UserResponseDto user = userService.activateUser(userId);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.activated"))
                .build();
    }

    @Operation(summary = "Kullanici rolunu degistir", description = "Kullanicinin guvenlik rolunu degistirerek yetki kapsamini gunceller")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kullanici rolu basariyla guncellendi"))
    @PatchMapping("/{userId}/role")
    public ApiResponse<UserResponseDto> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        UserResponseDto user = userService.updateUserRole(userId, request);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.role.updated"))
                .build();
    }
}

