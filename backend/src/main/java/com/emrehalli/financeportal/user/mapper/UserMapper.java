package com.emrehalli.financeportal.user.mapper;

import com.emrehalli.financeportal.admin.dto.AdminUpdateUserRequest;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.ProfileNoteItem;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class UserMapper {

    private final ObjectMapper objectMapper;

    public UserMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .active(user.isActive())
                .notes(deserializeNotes(user.getProfileNote()))
                .build();
    }

    public void applyProfileUpdate(User user, UpdateUserRequest request) {
        user.setFullName(request.getFullName());
        user.setPreferredLanguage(request.getPreferredLanguage());
        user.setThemePreference(request.getThemePreference());
        // notes null ise mevcut değer korunur; boş liste gönderilirse sıfırlanır
        if (request.getNotes() != null) {
            user.setProfileNote(serializeNotes(request.getNotes()));
        }
    }

    public void applyProfileUpdate(User user, AdminUpdateUserRequest request) {
        user.setFullName(request.getFullName());
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getPreferredLanguage() != null) {
            user.setPreferredLanguage(request.getPreferredLanguage());
        }
        if (request.getThemePreference() != null) {
            user.setThemePreference(request.getThemePreference());
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
    }

    private List<ProfileNoteItem> deserializeNotes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProfileNoteItem>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String serializeNotes(List<ProfileNoteItem> notes) {
        if (notes == null || notes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(notes);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
