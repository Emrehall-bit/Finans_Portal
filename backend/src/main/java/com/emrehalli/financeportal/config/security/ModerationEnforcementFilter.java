package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.admin.moderation.service.UserModerationService;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
public class ModerationEnforcementFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final UserModerationService userModerationService;
    private final ObjectMapper objectMapper;
    private final RequestMatcher publicEndpointsMatcher = new OrRequestMatcher(
            new AntPathRequestMatcher("/actuator/health", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/actuator/health/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/actuator/info", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/actuator/metrics", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/actuator/metrics/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/actuator/prometheus", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/admin/binance/history/fetch/status", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/admin/binance/tcmb/sync/status", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/admin/markets/fx/tcmb/history/backfill/status", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/admin/stocks/fetch/status", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/admin/stocks/history/backfill/status", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/news/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/markets/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/technical-analysis/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/ai/technical/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/ai/fundamental/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/ipos/**", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/futures/**", HttpMethod.GET.name())
    );

    public ModerationEnforcementFilter(
            UserService userService,
            UserModerationService userModerationService,
            ObjectMapper objectMapper
    ) {
        this.userService = userService;
        this.userModerationService = userModerationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        return publicEndpointsMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            User currentUser = userService.getCurrentAuthenticatedUserEntity();
            userModerationService.assertAccessAllowed(currentUser.getId());
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != HttpStatus.FORBIDDEN.value()) {
                throw exception;
            }

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.builder()
                    .success(false)
                    .data(null)
                    .message(exception.getReason())
                    .build());
        }
    }
}




