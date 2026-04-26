package com.emrehalli.financeportal.technicalanalysis.exception;

import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.emrehalli.financeportal.technicalanalysis")
public class TechnicalAnalysisExceptionHandler {

    private static final Logger logger = LogManager.getLogger(TechnicalAnalysisExceptionHandler.class);

    @ExceptionHandler(TechnicalAnalysisException.class)
    public ProblemDetail handleTechnicalAnalysisException(TechnicalAnalysisException ex, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : null;
        if (path != null) {
            LoggingContext.put(LoggingConstants.PATH_KEY, path);
        }

        try {
            if (ex.status().is5xxServerError()) {
                logger.error(
                        "Technical analysis request failed: status={}, message={}, path={}, requestId={}",
                        ex.status().value(),
                        ex.getMessage(),
                        path,
                        LoggingContext.get(LoggingConstants.REQUEST_ID_KEY),
                        ex
                );
            } else {
                logger.warn(
                        "Technical analysis request failed: status={}, message={}, path={}, requestId={}",
                        ex.status().value(),
                        ex.getMessage(),
                        path,
                        LoggingContext.get(LoggingConstants.REQUEST_ID_KEY)
                );
            }
        } finally {
            if (path != null) {
                LoggingContext.remove(LoggingConstants.PATH_KEY);
            }
        }

        ProblemDetail problemDetail = ProblemDetail.forStatus(ex.status());
        problemDetail.setTitle(resolveTitle(ex));
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setProperty("requestId", LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
        return problemDetail;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingServletRequestParameterException(MissingServletRequestParameterException ex,
                                                                       HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : null;
        if (path != null) {
            LoggingContext.put(LoggingConstants.PATH_KEY, path);
        }

        try {
            logger.warn(
                    "Technical analysis request parameter missing: parameter={}, path={}, requestId={}",
                    ex.getParameterName(),
                    path,
                    LoggingContext.get(LoggingConstants.REQUEST_ID_KEY)
            );
        } finally {
            if (path != null) {
                LoggingContext.remove(LoggingConstants.PATH_KEY);
            }
        }

        ProblemDetail problemDetail = ProblemDetail.forStatus(400);
        problemDetail.setTitle("Technical analysis validation error");
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setProperty("requestId", LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
        return problemDetail;
    }

    private String resolveTitle(TechnicalAnalysisException ex) {
        if (ex instanceof TechnicalAnalysisValidationException) {
            return "Technical analysis validation error";
        }

        if (ex instanceof TechnicalAnalysisNotFoundException) {
            return "Technical analysis data not found";
        }

        return "Technical analysis error";
    }
}
