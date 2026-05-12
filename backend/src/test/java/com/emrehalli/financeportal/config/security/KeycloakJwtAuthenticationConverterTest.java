package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void shouldParseUserPremiumRoleWithoutFailure() {
        Jwt jwt = jwtWithRoles("USER_PREMIUM");

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(converter.extractRole(jwt)).isEqualTo(UserRole.USER_PREMIUM);
        assertThat(authentication.getAuthorities()).contains(new SimpleGrantedAuthority("ROLE_USER_PREMIUM"));
    }

    @Test
    void shouldParseSystemEngineerRoleWithoutFailure() {
        Jwt jwt = jwtWithRoles("SYSTEM_ENGINEER");

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(converter.extractRole(jwt)).isEqualTo(UserRole.SYSTEM_ENGINEER);
        assertThat(authentication.getAuthorities()).contains(new SimpleGrantedAuthority("ROLE_SYSTEM_ENGINEER"));
    }

    private Jwt jwtWithRoles(String... roles) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "user-1",
                        "preferred_username", "tester",
                        "realm_access", Map.of("roles", List.of(roles))
                )
        );
    }
}
