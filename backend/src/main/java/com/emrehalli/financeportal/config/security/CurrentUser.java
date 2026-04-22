package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.entity.UserRole;

public record CurrentUser(
        String keycloakId,
        String email,
        String fullName,
        UserRole role,
        boolean authenticated,
        String authProvider
) {
}
