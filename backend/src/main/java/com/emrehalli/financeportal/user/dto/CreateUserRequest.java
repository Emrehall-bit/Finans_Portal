package com.emrehalli.financeportal.user.dto;

import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Yeni kullanıcı oluşturma isteği")
public class CreateUserRequest {

    @Schema(description = "Keycloak kullanıcı kimliği", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String keycloakId;

    @NotBlank(message = "Full name is required")
    @Schema(description = "Tam ad", example = "Emre Halli", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "E-posta adresi", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Builder.Default
    @Schema(description = "Kullanıcı rolü", example = "USER")
    private UserRole role = UserRole.USER;

    @Schema(description = "Tercih edilen dil", example = "TR")
    private PreferredLanguage preferredLanguage;

    @Schema(description = "Tema tercihi", example = "DARK")
    private ThemePreference themePreference;
}

