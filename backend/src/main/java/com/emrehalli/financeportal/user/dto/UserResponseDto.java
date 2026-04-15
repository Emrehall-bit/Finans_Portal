package com.emrehalli.financeportal.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {

    private Long id;
    private String keycloakId;
    private String fullName;
    private String email;
    private String preferredLanguage;
    private String themePreference;
    private LocalDateTime createdAt;
}