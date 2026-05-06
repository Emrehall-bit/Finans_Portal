package com.emrehalli.financeportal.user.dto;

import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Size(max = 255, message = "Full name must be 255 characters or fewer")
    private String fullName;

    private PreferredLanguage preferredLanguage;

    private ThemePreference themePreference;
}



