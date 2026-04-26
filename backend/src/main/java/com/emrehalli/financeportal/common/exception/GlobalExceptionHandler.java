package com.emrehalli.financeportal.common.exception;

import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.LoggingContext;
import com.emrehalli.financeportal.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Object> handleResourceNotFoundException(ResourceNotFoundException e, HttpServletRequest request) {
        logException("Resource not found", e, request, false);
        return ApiResponse.builder()
                .success(false)
                .data(null)
                .message(e.getMessage())
                .requestId(currentRequestId())
                .build();
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleBadRequestException(BadRequestException e, HttpServletRequest request) {
        logException("Bad request", e, request, false);
        return ApiResponse.builder()
                .success(false)
                .data(null)
                .message(e.getMessage())
                .requestId(currentRequestId())
                .build();
    }

    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Object> handleDuplicateResourceException(DuplicateResourceException e, HttpServletRequest request) {
        logException("Duplicate resource", e, request, false);
        return ApiResponse.builder()
                .success(false)
                .data(null)
                .message(e.getMessage())
                .requestId(currentRequestId())
                .build();
    }

    @ExceptionHandler(ProviderRateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Object> handleProviderRateLimitException(ProviderRateLimitException e, HttpServletRequest request) {
        logException("Provider rate limit", e, request, false);
        return ApiResponse.builder()
                .success(false)
                .data(null)
                .message(e.getMessage())
                .requestId(currentRequestId())
                .build();
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Object> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        logException("Resource not found", e, request, false);
        return ApiResponse.builder()
                .success(false)
                .data(null)
                .message("Resource not found")
                .requestId(currentRequestId())
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Object> handleException(Exception e, HttpServletRequest request) {
        logException("Unhandled exception occurred", e, request, true);

        return ApiResponse.builder()
                .success(false)
                .data(null)
                .message("Internal Server Error")
                .requestId(currentRequestId())
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        logException("Illegal argument", ex, request, false);
        return ResponseEntity.badRequest()
                .body(ApiResponse.builder()
                        .success(false)
                        .data(null)
                        .message(ex.getMessage())
                        .requestId(currentRequestId())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                                     HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");
        logException("Validation failed", ex, request, false);

        return ResponseEntity.badRequest()
                .body(ApiResponse.builder()
                        .success(false)
                        .data(null)
                        .message(message)
                        .requestId(currentRequestId())
                        .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex,
                                                                                         HttpServletRequest request) {
        logException("Method argument type mismatch", ex, request, false);
        return ResponseEntity.badRequest()
                .body(ApiResponse.builder()
                        .success(false)
                        .data(null)
                        .message(ex.getMessage())
                        .requestId(currentRequestId())
                        .build());
    }

    private void logException(String event, Exception exception, HttpServletRequest request, boolean includeStackTrace) {
        String requestId = currentRequestId();
        String path = request != null ? request.getRequestURI() : null;
        String method = request != null ? request.getMethod() : null;

        if (path != null) {
            LoggingContext.put(LoggingConstants.PATH_KEY, path);
        }
        if (method != null) {
            LoggingContext.put(LoggingConstants.METHOD_KEY, method);
        }

        try {
            if (includeStackTrace) {
                logger.error(
                        "{}: exceptionClass={}, message={}, method={}, path={}, requestId={}",
                        event,
                        exception.getClass().getSimpleName(),
                        exception.getMessage(),
                        method,
                        path,
                        requestId,
                        exception
                );
            } else {
                logger.warn(
                        "{}: exceptionClass={}, message={}, method={}, path={}, requestId={}",
                        event,
                        exception.getClass().getSimpleName(),
                        exception.getMessage(),
                        method,
                        path,
                        requestId
                );
            }
        } finally {
            if (path != null) {
                LoggingContext.remove(LoggingConstants.PATH_KEY);
            }
            if (method != null) {
                LoggingContext.remove(LoggingConstants.METHOD_KEY);
            }
        }
    }

    private String currentRequestId() {
        return LoggingContext.get(LoggingConstants.REQUEST_ID_KEY);
    }
}


