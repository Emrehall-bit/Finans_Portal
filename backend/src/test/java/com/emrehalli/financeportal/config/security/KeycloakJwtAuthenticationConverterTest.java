package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeycloakJwtAuthenticationConverterTest {

    @Test
    void localPremiumRole_isAddedToAuthorities() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByKeycloakId("user-1"))
                .thenReturn(Optional.of(User.builder().id(1L).keycloakId("user-1").email("u@example.com").fullName("User").role(UserRole.USER_PREMIUM).build()));

        KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter(userRepository);
        Jwt jwt = new Jwt(
                "token",
                null,
                null,
                Map.of("alg", "none"),
                Map.of(
                        "sub", "user-1",
                        "realm_access", Map.of("roles", List.of("USER")),
                        "email", "u@example.com"
                )
        );

        var token = converter.convert(jwt);

        assertThat(token.getAuthorities()).contains(new SimpleGrantedAuthority("ROLE_USER_PREMIUM"));
        assertThat(converter.extractRole(jwt)).isEqualTo(UserRole.USER_PREMIUM);
    }
}




