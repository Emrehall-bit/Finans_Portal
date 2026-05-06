package com.emrehalli.financeportal.user.dto;

import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    private String keycloakId;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Builder.Default
    private UserRole role = UserRole.USER;

    private PreferredLanguage preferredLanguage;
    private ThemePreference themePreference;
}



