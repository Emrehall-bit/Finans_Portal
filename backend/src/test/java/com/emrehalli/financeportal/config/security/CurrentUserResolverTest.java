package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTest {

    @Mock
    private KeycloakJwtRoleConverter keycloakJwtRoleConverter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolve_whenEmailClaimIsMissing_throwsBadRequestException() {
        CurrentUserResolver currentUserResolver = new CurrentUserResolver(keycloakJwtRoleConverter);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "kc-user-1")
                .claim("preferred_username", "preferred-user")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        assertThrows(BadRequestException.class, currentUserResolver::resolve);
    }

    @Test
    void resolve_whenEmailClaimExists_returnsCurrentUser() {
        CurrentUserResolver currentUserResolver = new CurrentUserResolver(keycloakJwtRoleConverter);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "kc-admin-1")
                .claim("email", "admin@example.com")
                .claim("name", "Admin User")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();

        when(keycloakJwtRoleConverter.extractRole(jwt)).thenReturn(UserRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        CurrentUser currentUser = currentUserResolver.resolve();

        assertEquals("kc-admin-1", currentUser.keycloakId());
        assertEquals("admin@example.com", currentUser.email());
        assertEquals("Admin User", currentUser.fullName());
        assertEquals(UserRole.ADMIN, currentUser.role());
        assertEquals("KEYCLOAK", currentUser.authProvider());
    }
}



