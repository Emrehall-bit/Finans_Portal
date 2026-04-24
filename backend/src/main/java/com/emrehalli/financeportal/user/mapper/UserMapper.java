package com.emrehalli.financeportal.user.mapper;

import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(CreateUserRequest request) {
        return User.builder()
                .keycloakId(request.getKeycloakId())
                .fullName(request.getFullName())
                .email(normalizeEmail(request.getEmail()))
                .role(request.getRole() != null ? request.getRole() : UserRole.USER)
                .preferredLanguage(request.getPreferredLanguage())
                .themePreference(request.getThemePreference())
                .build();
    }

    public UserResponseDto toResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .keycloakId(user.getKeycloakId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .preferredLanguage(user.getPreferredLanguage())
                .themePreference(user.getThemePreference())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public void applyProfileUpdate(User user, UpdateUserRequest request) {
        user.setFullName(request.getFullName());
        user.setPreferredLanguage(normalizeNullable(request.getPreferredLanguage()));
        user.setThemePreference(normalizeNullable(request.getThemePreference()));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}



