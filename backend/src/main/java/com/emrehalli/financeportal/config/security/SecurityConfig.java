package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.common.logging.AuditActivityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    private final ResourceAccessManager resourceAccessManager;
    private final ModerationEnforcementFilter moderationEnforcementFilter;
    private final AiPremiumAccessDeniedHandler aiPremiumAccessDeniedHandler;
    private final AuditActivityFilter auditActivityFilter;

    public SecurityConfig(KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter,
                          ResourceAccessManager resourceAccessManager,
                          ModerationEnforcementFilter moderationEnforcementFilter,
                          AiPremiumAccessDeniedHandler aiPremiumAccessDeniedHandler,
                          AuditActivityFilter auditActivityFilter) {
        this.keycloakJwtAuthenticationConverter = keycloakJwtAuthenticationConverter;
        this.resourceAccessManager = resourceAccessManager;
        this.moderationEnforcementFilter = moderationEnforcementFilter;
        this.aiPremiumAccessDeniedHandler = aiPremiumAccessDeniedHandler;
        this.auditActivityFilter = auditActivityFilter;
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/news/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Public endpoints
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/binance/history/fetch/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/binance/tcmb/sync/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/markets/fx/tcmb/history/backfill/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/stocks/fetch/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/stocks/history/backfill/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/news/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/markets/**").permitAll()
                        .requestMatchers("/api/v1/technical-analysis/*/indicators").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/technical-analysis/*/indicators").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/technical-analysis/indicators/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/technical-analysis/**").permitAll()

                        // Analiz modülü
                        .requestMatchers(HttpMethod.GET, "/api/v1/analysis/fundamental/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers("/api/v1/analysis/drawings/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers("/api/v1/premium/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/technical/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/fundamental/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/unified/**").hasAnyRole("USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/compare-analysis").hasAnyRole("USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/portfolio-analysis/{portfolioId}").access(resourceAccessManager::canAccessPremiumPortfolioAi)
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/news-impact/**").hasAnyRole("USER_PREMIUM", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/ai/cache/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/ai/cache").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/companies/**").permitAll()
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
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/simulations/**").hasAnyRole("USER", "USER_PREMIUM", "ADMIN")

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.accessDeniedHandler(aiPremiumAccessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(guestSafeBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
                .addFilterAfter(auditActivityFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(moderationEnforcementFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BearerTokenResolver guestSafeBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String token = delegate.resolve(request);
            if (token == null) {
                return null;
            }
            // "null"/"undefined" gibi sahte token değerlerini ve geçersiz JWT formatlarını yoksay
            if ("null".equalsIgnoreCase(token) || "undefined".equalsIgnoreCase(token)) {
                return null;
            }
            // Geçerli bir JWT en az 2 nokta içermeli (header.payload.signature)
            if (token.indexOf('.') == -1 || token.chars().filter(c -> c == '.').count() < 2) {
                return null;
            }
            return token;
        };
    }
}

