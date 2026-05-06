package com.emrehalli.financeportal.user.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.user.dto.AdminUpdateUserRequest;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AppMessageSource appMessageSource;

    public UserController(UserService userService, AppMessageSource appMessageSource) {
        this.userService = userService;
        this.appMessageSource = appMessageSource;
    }

    @PostMapping
    public ApiResponse<UserResponseDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponseDto user = userService.createUser(request);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.created"))
                .build();
    }

    @GetMapping("/admin")
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

    @GetMapping("/admin/{userId}")
    public ApiResponse<UserResponseDto> getUserById(@PathVariable Long userId) {
        UserResponseDto user = userService.getUserById(userId);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.fetched"))
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponseDto> getCurrentUserProfile() {
        UserProfileResponseDto profile = userService.getCurrentUserProfile();

        return ApiResponse.<UserProfileResponseDto>builder()
                .success(true)
                .data(profile)
                .message(appMessageSource.get("user.profile.fetched"))
                .build();
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponseDto> updateCurrentUserProfile(@Valid @RequestBody UpdateUserRequest request) {
        UserProfileResponseDto profile = userService.updateCurrentUserProfile(request);

        return ApiResponse.<UserProfileResponseDto>builder()
                .success(true)
                .data(profile)
                .message(appMessageSource.get("user.profile.updated"))
                .build();
    }

    @PutMapping("/admin/{userId}")
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

    @PatchMapping("/admin/{userId}/deactivate")
    public ApiResponse<UserResponseDto> deactivateUser(@PathVariable Long userId) {
        UserResponseDto user = userService.deactivateUser(userId);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.deactivated"))
                .build();
    }

    @PatchMapping("/admin/{userId}/activate")
    public ApiResponse<UserResponseDto> activateUser(@PathVariable Long userId) {
        UserResponseDto user = userService.activateUser(userId);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message(appMessageSource.get("user.activated"))
                .build();
    }
}



