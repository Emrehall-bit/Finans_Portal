package com.emrehalli.financeportal.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(LoggingConstants.REQUEST_ID_HEADER));
        request.setAttribute("requestId", requestId);
        request.setAttribute(LoggingConstants.REQUEST_ID_ATTR, requestId);
        LoggingContext.put(LoggingConstants.REQUEST_ID_KEY, requestId);
        response.setHeader(LoggingConstants.REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            LoggingContext.remove(LoggingConstants.REQUEST_ID_KEY);
        }
    }

    private String resolveRequestId(String requestIdHeader) {
        if (requestIdHeader == null || requestIdHeader.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestIdHeader.trim();
    }
}
