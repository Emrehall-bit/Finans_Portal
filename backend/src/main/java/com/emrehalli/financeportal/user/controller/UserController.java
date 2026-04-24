package com.emrehalli.financeportal.user.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ApiResponse<UserResponseDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponseDto user = userService.createUser(request);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message("User created successfully for admin/test usage")
                .build();
    }

    @GetMapping("/admin")
    public ApiResponse<List<UserResponseDto>> getAllUsers() {
        return ApiResponse.<List<UserResponseDto>>builder()
                .success(true)
                .data(userService.getAllUsers())
                .message("Users fetched successfully")
                .build();
    }

    @GetMapping("/admin/{userId}")
    public ApiResponse<UserResponseDto> getUserById(@PathVariable Long userId) {
        UserResponseDto user = userService.getUserById(userId);

        return ApiResponse.<UserResponseDto>builder()
                .success(true)
                .data(user)
                .message("User fetched successfully")
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponseDto> getCurrentUserProfile() {
        UserProfileResponseDto profile = userService.getCurrentUserProfile();

        return ApiResponse.<UserProfileResponseDto>builder()
                .success(true)
                .data(profile)
                .message("Current user profile fetched successfully")
                .build();
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponseDto> updateCurrentUserProfile(@Valid @RequestBody UpdateUserRequest request) {
        UserProfileResponseDto profile = userService.updateCurrentUserProfile(request);

        return ApiResponse.<UserProfileResponseDto>builder()
                .success(true)
                .data(profile)
                .message("Current user profile updated successfully")
                .build();
    }
}



