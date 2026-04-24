package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final KeycloakJwtRoleConverter keycloakJwtRoleConverter;

    public CurrentUserResolver(KeycloakJwtRoleConverter keycloakJwtRoleConverter) {
        this.keycloakJwtRoleConverter = keycloakJwtRoleConverter;
    }

    public CurrentUser resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("Authenticated user could not be resolved from JWT");
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadRequestException("JWT principal could not be resolved");
        }

        String email = jwt.getClaimAsString("email");
        if (isBlank(email)) {
            throw new BadRequestException("JWT token does not contain a valid email claim");
        }

        return new CurrentUser(
                jwt.getSubject(),
                email,
                firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username")),
                keycloakJwtRoleConverter.extractRole(jwt),
                true,
                "KEYCLOAK"
        );
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}


