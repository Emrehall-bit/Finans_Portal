package com.emrehalli.financeportal.technicalanalysis.exception;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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
    private final AppMessageSource appMessageSource;

    public TechnicalAnalysisExceptionHandler(AppMessageSource appMessageSource) {
        this.appMessageSource = appMessageSource;
    }

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
        problemDetail.setTitle(appMessageSource.get("technical.analysis.validation.title"));
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setProperty("requestId", LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(appMessageSource.get("technical.analysis.validation.title"));
        logger.warn("Technical analysis validation failed: message={}, requestId={}",
                message, LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));

        ProblemDetail problemDetail = ProblemDetail.forStatus(400);
        problemDetail.setTitle(appMessageSource.get("technical.analysis.validation.title"));
        problemDetail.setDetail(message);
        problemDetail.setProperty("requestId", LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
        return problemDetail;
    }

    private String resolveTitle(TechnicalAnalysisException ex) {
        if (ex instanceof TechnicalAnalysisException.Validation) {
            return appMessageSource.get("technical.analysis.validation.title");
        }

        if (ex instanceof TechnicalAnalysisException.NotFound) {
            return appMessageSource.get("technical.analysis.notFound.title");
        }

        return appMessageSource.get("technical.analysis.error.title");
    }
}




