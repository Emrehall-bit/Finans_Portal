package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import com.emrehalli.financeportal.common.logging.StructuredLogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class AiPremiumAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger logger = LogManager.getLogger(AiPremiumAccessDeniedHandler.class);

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        String uri    = request.getRequestURI();
        String userId = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName)
                .orElse("anonymous");

        String message;
        String code;

        if (isPremiumAiPath(uri)) {
            message = "This AI feature requires premium access.";
            code    = "PREMIUM_REQUIRED";
        } else {
            message = "Access denied.";
            code    = "ACCESS_DENIED";
        }

        try (StructuredLogContext ignored = StructuredLogContext.open()
                .put(LoggingConstants.LOG_TYPE_KEY, "security_event")
                .put(LoggingConstants.URI_KEY, uri)
                .put(LoggingConstants.STATUS_KEY, "403")
                .put(LoggingConstants.USER_ID_KEY, "anonymous".equals(userId) ? null : userId)) {
            logger.warn("FORBIDDEN_ACCESS code={} uri={} userId={}", code, uri, userId);
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                String.format("{\"error\":\"%s\",\"code\":\"%s\",\"status\":403}", message, code)
        );
    }

    private boolean isPremiumAiPath(String uri) {
        return uri.startsWith("/api/v1/ai/unified")
            || uri.startsWith("/api/v1/ai/news-impact");
    }
}




