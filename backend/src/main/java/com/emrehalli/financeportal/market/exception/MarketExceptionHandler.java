package com.emrehalli.financeportal.market.exception;

import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.emrehalli.financeportal.market")
public class MarketExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketExceptionHandler.class);

    @ExceptionHandler(MarketDataNotFoundException.class)
    public ProblemDetail handleMarketDataNotFound(MarketDataNotFoundException ex, HttpServletRequest request) {
        logException("Market data not found", ex, request);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Market data not found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("requestId", LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        logException("Invalid market request parameter", ex, request);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid market request parameter");
        problem.setDetail(ex.getMessage());
        problem.setProperty("requestId", LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        logException("Market module error", ex, request);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Market module error");
        problem.setDetail(ex.getMessage());
        problem.setProperty("requestId", LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
        return problem;
    }

    private void logException(String event, Exception exception, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : null;
        if (path != null) {
            LoggingContext.put(LoggingConstants.PATH_KEY, path);
        }

        try {
            log.error(
                    "{}: exceptionClass={}, message={}, path={}, requestId={}",
                    event,
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    path,
                    LoggingContext.get(LoggingConstants.REQUEST_ID_KEY),
                    exception
            );
        } finally {
            if (path != null) {
                LoggingContext.remove(LoggingConstants.PATH_KEY);
            }
        }
    }
}
