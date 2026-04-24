package com.emrehalli.financeportal.user.dto;

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

    @Size(max = 50, message = "Preferred language must be 50 characters or fewer")
    private String preferredLanguage;

    @Size(max = 50, message = "Theme preference must be 50 characters or fewer")
    private String themePreference;
}



