package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.entity.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class KeycloakJwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakJwtAuthenticationConverter delegate;

    public KeycloakJwtRoleConverter(KeycloakJwtAuthenticationConverter delegate) {
        this.delegate = delegate;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }

    public UserRole extractRole(Jwt jwt) {
        return delegate.extractRole(jwt);
    }
}



