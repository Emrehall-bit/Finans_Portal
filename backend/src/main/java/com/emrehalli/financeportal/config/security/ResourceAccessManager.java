package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import com.emrehalli.financeportal.user.repository.UserRepository;
import com.emrehalli.financeportal.watchlist.repository.WatchlistRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class ResourceAccessManager {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final WatchlistRepository watchlistRepository;

    public ResourceAccessManager(UserRepository userRepository,
                                 PortfolioRepository portfolioRepository,
                                 WatchlistRepository watchlistRepository) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.watchlistRepository = watchlistRepository;
    }

    public AuthorizationDecision canAccessUserId(Supplier<Authentication> authentication,
                                                 RequestAuthorizationContext context) {
        String keycloakId = extractSubject(authentication.get());
        Long userId = parseId(context.getVariables(), "userId");

        boolean granted = keycloakId != null
                && userId != null
                && userRepository.findById(userId)
                .map(user -> keycloakId.equals(user.getKeycloakId()))
                .orElse(false);

        return new AuthorizationDecision(granted);
    }

    public AuthorizationDecision canAccessPortfolioId(Supplier<Authentication> authentication,
                                                      RequestAuthorizationContext context) {
        String keycloakId = extractSubject(authentication.get());
        Long portfolioId = parseId(context.getVariables(), "portfolioId");

        boolean granted = keycloakId != null
                && portfolioId != null
                && portfolioRepository.existsByIdAndUserKeycloakId(portfolioId, keycloakId);

        return new AuthorizationDecision(granted);
    }

    public AuthorizationDecision canAccessWatchlistId(Supplier<Authentication> authentication,
                                                      RequestAuthorizationContext context) {
        String keycloakId = extractSubject(authentication.get());
        Long watchlistId = parseId(context.getVariables(), "id");

        boolean granted = keycloakId != null
                && watchlistId != null
                && watchlistRepository.existsByIdAndUserKeycloakId(watchlistId, keycloakId);

        return new AuthorizationDecision(granted);
    }

    private String extractSubject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        return null;
    }

    private Long parseId(Map<String, String> variables, String variableName) {
        String rawValue = variables.get(variableName);
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(rawValue);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
