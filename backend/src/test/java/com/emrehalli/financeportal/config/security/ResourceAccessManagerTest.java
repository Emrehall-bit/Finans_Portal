package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import com.emrehalli.financeportal.watchlist.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAccessManagerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @InjectMocks
    private ResourceAccessManager resourceAccessManager;

    @Test
    void canAccessPortfolioId_whenPortfolioBelongsToAuthenticatedUser_returnsGranted() {
        when(portfolioRepository.existsByIdAndUserKeycloakId(25L, "kc-user-1")).thenReturn(true);

        boolean granted = resourceAccessManager.canAccessPortfolioId(authentication("kc-user-1"), context("portfolioId", "25"))
                .isGranted();

        assertTrue(granted);
    }

    @Test
    void canAccessPortfolioId_whenPortfolioBelongsToAnotherUser_returnsDenied() {
        boolean granted = resourceAccessManager.canAccessPortfolioId(authentication("kc-user-2"), context("portfolioId", "25"))
                .isGranted();

        assertFalse(granted);
    }

    @Test
    void canAccessUserId_whenUserMatchesJwtSubject_returnsGranted() {
        when(userRepository.findById(5L))
                .thenReturn(Optional.of(User.builder().id(5L).keycloakId("kc-user-1").fullName("User").email("u@example.com").build()));

        boolean granted = resourceAccessManager.canAccessUserId(authentication("kc-user-1"), context("userId", "5"))
                .isGranted();

        assertTrue(granted);
    }

    @Test
    void canAccessUserId_whenAuthenticationIsNotJwt_returnsDenied() {
        TestingAuthenticationToken token = new TestingAuthenticationToken("plain-user", "secret");
        token.setAuthenticated(true);
        Supplier<Authentication> authentication = () -> token;

        boolean granted = resourceAccessManager.canAccessUserId(authentication, context("userId", "5")).isGranted();

        assertFalse(granted);
    }

    private Supplier<Authentication> authentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", subject)
                .build();
        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return () -> authentication;
    }

    private RequestAuthorizationContext context(String variableName, String value) {
        RequestAuthorizationContext context = mock(RequestAuthorizationContext.class);
        when(context.getVariables()).thenReturn(Map.of(variableName, value));
        return context;
    }
}



