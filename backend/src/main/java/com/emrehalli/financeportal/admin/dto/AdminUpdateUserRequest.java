package com.emrehalli.financeportal.admin.dto;

import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import com.emrehalli.financeportal.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Admin kullanıcı güncelleme isteği")
public class AdminUpdateUserRequest {

    @Size(max = 255, message = "Full name must be 255 characters or fewer")
    @Schema(description = "Tam ad", example = "Emre Halli")
    private String fullName;

    @Schema(description = "Kullanıcı rolü", example = "USER_PREMIUM")
    private UserRole role;

    @Schema(description = "Tercih edilen dil", example = "TR")
    private PreferredLanguage preferredLanguage;

    @Schema(description = "Tema tercihi", example = "DARK")
    private ThemePreference themePreference;

    @Schema(description = "Aktiflik durumu", example = "true")
    private Boolean active;
}

