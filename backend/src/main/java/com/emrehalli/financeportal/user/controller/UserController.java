package com.emrehalli.financeportal.user.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.service.UserService;
import jakarta.validation.Valid;
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

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteCurrentUserAccount() {
        userService.deleteCurrentUserAccount();

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Account deleted successfully")
                .build();
    }
}







