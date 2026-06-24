package com.emrehalli.financeportal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Kullanıcı profil yanıt modeli")
public class UserProfileResponseDto {

    @Schema(description = "Kullanıcı bilgileri")
    private UserResponseDto user;

    @Schema(description = "Kimlik doğrulama durumu", example = "true")
    private boolean authenticated;

    @Schema(description = "Kimlik sağlayıcı", example = "keycloak")
    private String authProvider;
}

