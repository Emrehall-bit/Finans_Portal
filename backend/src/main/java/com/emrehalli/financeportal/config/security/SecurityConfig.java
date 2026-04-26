package com.emrehalli.financeportal.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Admin endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/news/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/users/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Public endpoints
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/news/**").permitAll()

                        // Alert endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/alerts/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/alerts/user/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/alerts/{userId}/{alertId}/cancel").access(resourceAccessManager::canAccessUserId)

                        // Portfolio endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/portfolios/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/user/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/portfolios/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}/summary").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}/details").access(resourceAccessManager::canAccessPortfolioId)

                        // Portfolio holdings endpoints
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolio-holdings/portfolio/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolio-holdings/portfolio/{portfolioId}/summary").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.POST, "/api/v1/portfolio-holdings/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/portfolio-holdings/{portfolioId}/{holdingId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/portfolio-holdings/{portfolioId}/{holdingId}").access(resourceAccessManager::canAccessPortfolioId)

                        // Watchlist endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/watchlist/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/watchlist/user/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/watchlist/{id}").access(resourceAccessManager::canAccessWatchlistId)

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtRoleConverter)));

        return http.build();
    }
}

