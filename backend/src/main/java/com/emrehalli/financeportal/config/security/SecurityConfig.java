package com.emrehalli.financeportal.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
public class SecurityConfig {

    private final KeycloakJwtRoleConverter keycloakJwtRoleConverter;
    private final ResourceAccessManager resourceAccessManager;

    public SecurityConfig(KeycloakJwtRoleConverter keycloakJwtRoleConverter,
                          ResourceAccessManager resourceAccessManager) {
        this.keycloakJwtRoleConverter = keycloakJwtRoleConverter;
        this.resourceAccessManager = resourceAccessManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   HandlerMappingIntrospector introspector) throws Exception {
        MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                 .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                 .authorizeHttpRequests(auth -> auth
                         .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/v1/users")).permitAll()
                         .requestMatchers(mvc.pattern(HttpMethod.GET, "/actuator/health")).permitAll()
                         .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/markets/**")).permitAll()
                         .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/market-events/**")).permitAll()
                         .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/v1/alerts/{userId}")).access(resourceAccessManager::canAccessUserId)
                         .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/alerts/user/{userId}")).access(resourceAccessManager::canAccessUserId)
                         .requestMatchers(mvc.pattern(HttpMethod.PATCH, "/api/v1/alerts/{userId}/{alertId}/cancel")).access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/v1/portfolios/{userId}")).access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/portfolios/user/{userId}")).access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/v1/portfolios/{portfolioId}")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}/summary")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}/details")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/portfolio-holdings/portfolio/{portfolioId}")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/portfolio-holdings/portfolio/{portfolioId}/summary")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/v1/portfolio-holdings/{portfolioId}")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/v1/portfolio-holdings/{portfolioId}/{holdingId}")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.DELETE, "/api/v1/portfolio-holdings/{portfolioId}/{holdingId}")).access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/v1/watchlist/{userId}")).access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/watchlist/user/{userId}")).access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(mvc.pattern(HttpMethod.DELETE, "/api/v1/watchlist/{id}")).access(resourceAccessManager::canAccessWatchlistId)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtRoleConverter)));

        return http.build();
    }
}
