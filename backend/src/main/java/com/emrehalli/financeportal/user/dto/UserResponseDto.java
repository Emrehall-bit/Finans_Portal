package com.emrehalli.financeportal.user.dto;

import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import com.emrehalli.financeportal.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Kullanıcı bilgi yanıt modeli")
public class UserResponseDto {

    @Schema(description = "Kullanıcı ID", example = "1")
    private Long id;

    @Schema(description = "Keycloak kullanıcı kimliği", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String keycloakId;

    @Schema(description = "Tam ad", example = "Emre Halli")
    private String fullName;

    @Schema(description = "E-posta adresi", example = "user@example.com")
    private String email;

    @Schema(description = "Kullanıcı rolü", example = "USER")
    private UserRole role;

    @Schema(description = "Tercih edilen dil", example = "TR")
    private PreferredLanguage preferredLanguage;

    @Schema(description = "Tema tercihi", example = "DARK")
    private ThemePreference themePreference;

    @Schema(description = "Kayıt tarihi")
    private LocalDateTime createdAt;

    @Schema(description = "Son güncelleme tarihi")
    private LocalDateTime updatedAt;

    @Schema(description = "Son giriş tarihi")
    private LocalDateTime lastLoginAt;

    @Schema(description = "Hesap aktiflik durumu", example = "true")
    private boolean active;

    @Schema(description = "Profil notları")
    private List<ProfileNoteItem> notes;
}

