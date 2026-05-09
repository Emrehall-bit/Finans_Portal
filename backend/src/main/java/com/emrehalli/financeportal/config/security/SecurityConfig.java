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

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    private final ResourceAccessManager resourceAccessManager;

    public SecurityConfig(KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter,
                          ResourceAccessManager resourceAccessManager) {
        this.keycloakJwtAuthenticationConverter = keycloakJwtAuthenticationConverter;
        this.resourceAccessManager = resourceAccessManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

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
                        .requestMatchers(HttpMethod.GET, "/api/v1/markets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/technical-analysis/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ipos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/futures/**").permitAll()

                        // Owner-based user endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/alerts/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/alerts/user/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/alerts/{userId}/{alertId}/cancel").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.POST, "/api/v1/portfolios/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/user/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/portfolios/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/portfolios/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}/summary").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolios/{portfolioId}/details").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolio-holdings/portfolio/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/portfolio-holdings/portfolio/{portfolioId}/summary").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.POST, "/api/v1/portfolio-holdings/{portfolioId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/portfolio-holdings/{portfolioId}/{holdingId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/portfolio-holdings/{portfolioId}/{holdingId}").access(resourceAccessManager::canAccessPortfolioId)
                        .requestMatchers(HttpMethod.POST, "/api/v1/watchlist/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.GET, "/api/v1/watchlist/user/{userId}").access(resourceAccessManager::canAccessUserId)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/watchlist/{id}").access(resourceAccessManager::canAccessWatchlistId)

                        // USER and above
                        .requestMatchers("/api/v1/portfolio/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers("/api/v1/portfolios/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers("/api/v1/portfolio-holdings/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers("/api/v1/watchlist/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers("/api/v1/alerts/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers("/api/v1/simulations/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)));

        return http.build();
    }
}
