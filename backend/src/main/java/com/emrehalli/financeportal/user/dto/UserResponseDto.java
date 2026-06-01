package com.emrehalli.financeportal.user.dto;

import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import com.emrehalli.financeportal.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {

    private Long id;
    private String keycloakId;
    private String fullName;
    private String email;
    private UserRole role;
    private PreferredLanguage preferredLanguage;
    private ThemePreference themePreference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private boolean active;
    private List<ProfileNoteItem> notes;
}







