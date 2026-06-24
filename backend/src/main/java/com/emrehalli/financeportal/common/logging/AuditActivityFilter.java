package com.emrehalli.financeportal.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuditActivityFilter extends OncePerRequestFilter {

    private static final Pattern PORTFOLIO_ID_PATTERN = Pattern.compile("^/api/v1/portfolios/(\\d+)(?:/.*)?$");
    private static final Pattern PORTFOLIO_CREATE_PATTERN = Pattern.compile("^/api/v1/portfolios/(\\d+)$");
    private static final Pattern ALERT_CREATE_PATTERN = Pattern.compile("^/api/v1/alerts/(\\d+)$");
    private static final Pattern ALERT_CANCEL_PATTERN = Pattern.compile("^/api/v1/alerts/(\\d+)/(\\d+)/cancel$");
    private static final Pattern ADMIN_USER_ROLE_PATTERN = Pattern.compile("^/api/v1/admin/users/(\\d+)/role$");
    private static final Pattern AUTH_LOGIN_PATTERN = Pattern.compile("^/(?:api/v1/)?auth/login$");
    private static final Pattern AUTH_LOGOUT_PATTERN = Pattern.compile("^/(?:api/v1/)?auth/logout$");

    private final AuditEventLogger auditEventLogger;

    public AuditActivityFilter(AuditEventLogger auditEventLogger) {
        this.auditEventLogger = auditEventLogger;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            resolveAuditTarget(request, response).ifPresent(target -> auditEventLogger.log(toEvent(
                    request,
                    response,
                    target,
                    (System.nanoTime() - startedAt) / 1_000_000
            )));
        }
    }

    private Optional<AuditTarget> resolveAuditTarget(HttpServletRequest request, HttpServletResponse response) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();

        if ("POST".equals(method) && AUTH_LOGIN_PATTERN.matcher(path).matches()) {
            return Optional.of(new AuditTarget(status < 400 ? AuditAction.LOGIN_SUCCESS : AuditAction.LOGIN_FAILURE,
                    "AUTH", null));
        }
        if (AUTH_LOGOUT_PATTERN.matcher(path).matches()) {
            return Optional.of(new AuditTarget(AuditAction.LOGOUT, "AUTH", null));
        }

        Matcher portfolioCreate = PORTFOLIO_CREATE_PATTERN.matcher(path);
        if ("POST".equals(method) && portfolioCreate.matches()) {
            return Optional.of(new AuditTarget(AuditAction.PORTFOLIO_CREATE, "PORTFOLIO", null));
        }

        Matcher portfolioId = PORTFOLIO_ID_PATTERN.matcher(path);
        if ("PUT".equals(method) && portfolioId.matches()) {
            return Optional.of(new AuditTarget(AuditAction.PORTFOLIO_UPDATE, "PORTFOLIO", portfolioId.group(1)));
        }
        if ("DELETE".equals(method) && portfolioId.matches()) {
            return Optional.of(new AuditTarget(AuditAction.PORTFOLIO_DELETE, "PORTFOLIO", portfolioId.group(1)));
        }

        Matcher alertCreate = ALERT_CREATE_PATTERN.matcher(path);
        if ("POST".equals(method) && alertCreate.matches()) {
            return Optional.of(new AuditTarget(AuditAction.ALARM_CREATE, "ALARM", null));
        }

        Matcher alertCancel = ALERT_CANCEL_PATTERN.matcher(path);
        if ("PATCH".equals(method) && alertCancel.matches()) {
            return Optional.of(new AuditTarget(AuditAction.ALARM_DELETE, "ALARM", alertCancel.group(2)));
        }

        Matcher roleChange = ADMIN_USER_ROLE_PATTERN.matcher(path);
        if ("PATCH".equals(method) && roleChange.matches()) {
            return Optional.of(new AuditTarget(AuditAction.USER_ROLE_CHANGE, "USER", roleChange.group(1)));
        }

        if (path.startsWith("/api/v1/admin/")) {
            return Optional.of(new AuditTarget(AuditAction.ADMIN_ACTION, "ADMIN_ENDPOINT", path));
        }

        if (isCriticalEndpoint(path)) {
            return Optional.of(new AuditTarget(AuditAction.CRITICAL_ENDPOINT_ACCESS, "CRITICAL_ENDPOINT", path));
        }

        return Optional.empty();
    }

    private AuditEvent toEvent(HttpServletRequest request,
                               HttpServletResponse response,
                               AuditTarget target,
                               long durationMs) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = authentication != null && authentication.getPrincipal() instanceof Jwt principal ? principal : null;

        String userId = firstNonBlank(
                LoggingContext.get(LoggingConstants.USER_ID_KEY),
                jwt != null ? jwt.getSubject() : null,
                authentication != null ? authentication.getName() : null
        );

        String username = firstNonBlank(
                jwtClaim(jwt, "preferred_username"),
                jwtClaim(jwt, "email"),
                authentication != null ? authentication.getName() : null
        );

        String role = authentication == null ? null : firstAuthority(authentication.getAuthorities());
        String endpoint = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            endpoint = endpoint + "?" + SensitiveDataMasker.maskQueryString(request.getQueryString());
        }

        return new AuditEvent(
                Instant.now(),
                userId,
                username,
                role,
                target.action(),
                target.resourceType(),
                target.resourceId(),
                endpoint,
                request.getMethod(),
                clientIp(request),
                request.getHeader("User-Agent"),
                response.getStatus() < 400,
                durationMs
        );
    }

    private boolean isCriticalEndpoint(String path) {
        return path.startsWith("/api/v1/ai/unified/")
                || path.startsWith("/api/v1/ai/news-impact/")
                || path.startsWith("/api/v1/portfolio-holdings/")
                || path.startsWith("/api/v1/watchlist/");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }

    private String firstAuthority(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return null;
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String jwtClaim(Jwt jwt, String claim) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaims().get(claim);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"anonymousUser".equals(value)) {
                return value;
            }
        }
        return null;
    }

    private record AuditTarget(AuditAction action, String resourceType, String resourceId) {
    }
}

