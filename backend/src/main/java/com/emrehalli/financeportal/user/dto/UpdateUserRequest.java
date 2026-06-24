package com.emrehalli.financeportal.user.dto;

import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Kullanıcı profil güncelleme isteği")
public class UpdateUserRequest {

    @Size(max = 255, message = "Full name must be 255 characters or fewer")
    @Schema(description = "Tam ad", example = "Emre Halli")
    private String fullName;

    @Schema(description = "Tercih edilen dil", example = "TR")
    private PreferredLanguage preferredLanguage;

    @Schema(description = "Tema tercihi", example = "DARK")
    private ThemePreference themePreference;

    @Valid
    @Schema(description = "Profil notları")
    private List<ProfileNoteItem> notes;
}

