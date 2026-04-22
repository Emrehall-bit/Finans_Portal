package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.entity.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class KeycloakJwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();

        UserRole role = extractRole(jwt);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));

        return new JwtAuthenticationToken(jwt, authorities, resolvePrincipalName(jwt));
    }

    public UserRole extractRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return UserRole.USER;
        }

        Object rolesObject = realmAccess.get("roles");
        if (!(rolesObject instanceof List<?> roles)) {
            return UserRole.USER;
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::toUpperCase)
                .anyMatch("ADMIN"::equals)
                ? UserRole.ADMIN
                : UserRole.USER;
    }

    private String resolvePrincipalName(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        return jwt.getSubject();
    }
}
