package com.emrehalli.financeportal.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

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
        request.setAttribute(LoggingConstants.REQUEST_START_TIME_ATTR, startedAt);
        ContentCachingRequestWrapper wrappedRequest = request instanceof ContentCachingRequestWrapper cachingRequest
                ? cachingRequest
                : new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = response instanceof ContentCachingResponseWrapper cachingResponse
                ? cachingResponse
                : new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            try {
                logRequest(wrappedRequest, wrappedResponse, startedAt);
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    private void logRequest(ContentCachingRequestWrapper requestWrapper,
                            ContentCachingResponseWrapper responseWrapper,
                            long startedAt) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

        try {
            String requestId = Optional.ofNullable((String) requestWrapper.getAttribute(LoggingConstants.REQUEST_ID_ATTR))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .or(() -> Optional.ofNullable((String) requestWrapper.getAttribute("requestId")))
                    .orElse(LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));

            if (requestId != null) {
                LoggingContext.put(LoggingConstants.REQUEST_ID_KEY, requestId);
            }
            LoggingContext.put(LoggingConstants.METHOD_KEY, requestWrapper.getMethod());
            LoggingContext.put(LoggingConstants.URI_KEY, requestWrapper.getRequestURI());
            LoggingContext.put(LoggingConstants.STATUS_KEY, String.valueOf(responseWrapper.getStatus()));
            LoggingContext.put(LoggingConstants.DURATION_MS_KEY, String.valueOf(durationMs));
            LoggingContext.put(LoggingConstants.LOG_TYPE_KEY, "access");
            String userId = resolveUserId();
            if (userId != null) {
                LoggingContext.put(LoggingConstants.USER_ID_KEY, userId);
            }

            if (requestWrapper.getQueryString() != null) {
                LoggingContext.put(LoggingConstants.QUERY_STRING_KEY, SensitiveDataMasker.maskQueryString(requestWrapper.getQueryString()));
            }

            log.info("HTTP request completed");
        } finally {
            LoggingContext.remove(LoggingConstants.REQUEST_ID_KEY);
            LoggingContext.remove(LoggingConstants.METHOD_KEY);
            LoggingContext.remove(LoggingConstants.URI_KEY);
            LoggingContext.remove(LoggingConstants.QUERY_STRING_KEY);
            LoggingContext.remove(LoggingConstants.STATUS_KEY);
            LoggingContext.remove(LoggingConstants.DURATION_MS_KEY);
            LoggingContext.remove(LoggingConstants.LOG_TYPE_KEY);
            LoggingContext.remove(LoggingConstants.USER_ID_KEY);
        }
    }

    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }

        String name = authentication.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }
}
