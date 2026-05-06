package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.entity.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt), resolvePrincipalName(jwt));
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return authorities;
        }

        Object rolesObject = realmAccess.get("roles");
        if (!(rolesObject instanceof List<?> roles)) {
            return authorities;
        }

        roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(String::toUpperCase)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);

        return authorities;
    }

    public UserRole extractRole(Jwt jwt) {
        return extractRealmRoles(jwt).stream()
                .map(this::toUserRole)
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(this::rolePriority))
                .orElse(UserRole.USER);
    }

    private String resolvePrincipalName(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        return jwt.getSubject();
    }

    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }

        Object rolesObject = realmAccess.get("roles");
        if (!(rolesObject instanceof List<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(String::toUpperCase)
                .toList();
    }

    private Optional<UserRole> toUserRole(String role) {
        try {
            return Optional.of(UserRole.valueOf(role));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private int rolePriority(UserRole role) {
        return switch (role) {
            case ADMIN -> 2;
            case USER -> 1;
        };
    }
}
