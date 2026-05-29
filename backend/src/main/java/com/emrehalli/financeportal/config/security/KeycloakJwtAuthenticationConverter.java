package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.repository.UserRepository;
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

    private final UserRepository userRepository;

    public KeycloakJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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

        resolveLocalRole(jwt)
                .map(UserRole::name)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .ifPresent(authorities::add);

        return authorities;
    }

    public UserRole extractRole(Jwt jwt) {
        return java.util.stream.Stream.concat(
                        extractRealmRoles(jwt).stream()
                .map(this::toUserRole)
                .flatMap(Optional::stream),
                        resolveLocalRole(jwt).stream())
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
            case ADMIN -> 5;
            case SYSTEM_ENGINEER -> 4;
            case USER_PREMIUM -> 3;
            case USER -> 2;
            case GUEST -> 1;
        };
    }

    private Optional<UserRole> resolveLocalRole(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            Optional<UserRole> bySubject = userRepository.findByKeycloakId(subject).map(user -> user.getRole());
            if (bySubject.isPresent()) {
                return bySubject;
            }
        }

        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email.trim().toLowerCase()).map(user -> user.getRole());
        }
        return Optional.empty();
    }
}




